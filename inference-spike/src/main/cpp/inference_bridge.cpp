#include <jni.h>
#include <llama.h>

#include <atomic>
#include <chrono>
#include <cstring>
#include <memory>
#include <mutex>
#include <new>
#include <stdexcept>
#include <vector>

namespace {
using CancelFlag = std::atomic_bool;
using Clock = std::chrono::steady_clock;

class UtfChars {
public:
    UtfChars(JNIEnv* env, jstring text) : env_(env), text_(text), chars_(env->GetStringUTFChars(text, nullptr)) {}
    ~UtfChars() { if (chars_ != nullptr) env_->ReleaseStringUTFChars(text_, chars_); }
    const char* get() const { return chars_; }

private:
    JNIEnv* env_;
    jstring text_;
    const char* chars_;
};

class Batch {
public:
    explicit Batch(int capacity) : value(llama_batch_init(capacity, 0, 1)) {}
    ~Batch() { llama_batch_free(value); }
    llama_batch value;
};

bool aborted(void* data) {
    return static_cast<const CancelFlag*>(data)->load(std::memory_order_relaxed);
}

jlong elapsed(Clock::time_point start) {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(Clock::now() - start).count();
}

jlongArray result(JNIEnv* env, int promptTokens, int outputTokens, jlong first, jlong decodeNanos, bool cancelled) {
    const jlong values[] = {promptTokens, outputTokens, first, decodeNanos, cancelled};
    jlongArray array = env->NewLongArray(5);
    if (array != nullptr) env->SetLongArrayRegion(array, 0, 5, values);
    return array;
}

void throwJava(JNIEnv* env, const char* message) {
    if (auto type = env->FindClass("java/lang/IllegalStateException"); type != nullptr) {
        env->ThrowNew(type, message);
        env->DeleteLocalRef(type);
    }
}
} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_omninumeris_inferencespike_NativeInference_createCancellation(JNIEnv* env, jobject) {
    auto* flag = new (std::nothrow) CancelFlag(false);
    if (flag == nullptr) throwJava(env, "Cannot allocate the inference cancellation flag");
    return reinterpret_cast<jlong>(flag);
}

extern "C" JNIEXPORT void JNICALL
Java_com_omninumeris_inferencespike_NativeInference_cancel(JNIEnv*, jobject, jlong handle) {
    reinterpret_cast<CancelFlag*>(handle)->store(true, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_omninumeris_inferencespike_NativeInference_destroyCancellation(JNIEnv*, jobject, jlong handle) {
    delete reinterpret_cast<CancelFlag*>(handle);
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_omninumeris_inferencespike_NativeInference_generate(
    JNIEnv* env, jobject, jstring path, jstring prompt, jint contextSize, jint maxTokens, jint threads, jlong handle
) {
    try {
        if (contextSize < 128 || contextSize > 512 || maxTokens < 1 || maxTokens > 128 || threads < 1 || threads > 4) {
            throw std::runtime_error("Benchmark parameters are outside the bounded profile");
        }
        auto* flag = reinterpret_cast<CancelFlag*>(handle);
        if (aborted(flag)) return result(env, 0, 0, 0, 0, true);

        const UtfChars modelPath(env, path);
        if (modelPath.get() == nullptr) return nullptr;
        const UtfChars text(env, prompt);
        if (text.get() == nullptr) return nullptr;
        const auto start = Clock::now();
        static std::once_flag backendInit;
        std::call_once(backendInit, [] {
            llama_log_set([](ggml_log_level, const char*, void*) {}, nullptr);
            llama_backend_init();
        });

        auto modelParams = llama_model_default_params();
        modelParams.n_gpu_layers = 0;
        modelParams.load_mode = LLAMA_LOAD_MODE_MMAP;
        modelParams.progress_callback = [](float, void* data) { return !aborted(data); };
        modelParams.progress_callback_user_data = flag;
        const std::unique_ptr<llama_model, decltype(&llama_model_free)> model(
            llama_model_load_from_file(modelPath.get(), modelParams), llama_model_free);
        if (aborted(flag)) return result(env, 0, 0, 0, 0, true);
        if (!model) throw std::runtime_error("Cannot load the pinned GGUF model");

        const auto* vocabulary = llama_model_get_vocab(model.get());
        std::vector<llama_token> tokens(contextSize);
        const int promptTokens = llama_tokenize(vocabulary, text.get(), static_cast<int>(std::strlen(text.get())),
            tokens.data(), contextSize, true, true);
        if (promptTokens < 1 || promptTokens > contextSize - maxTokens) {
            throw std::runtime_error("Prompt and generation exceed the context budget");
        }

        auto contextParams = llama_context_default_params();
        contextParams.n_ctx = contextSize;
        contextParams.n_batch = contextSize;
        contextParams.n_ubatch = contextSize;
        contextParams.n_threads = threads;
        contextParams.n_threads_batch = threads;
        contextParams.abort_callback = aborted;
        contextParams.abort_callback_data = flag;
        const std::unique_ptr<llama_context, decltype(&llama_free)> context(
            llama_init_from_model(model.get(), contextParams), llama_free);
        if (aborted(flag)) return result(env, promptTokens, 0, 0, 0, true);
        if (!context) throw std::runtime_error("Cannot create the bounded inference context");

        const std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(
            llama_sampler_init_greedy(), llama_sampler_free);
        if (!sampler) throw std::runtime_error("Cannot create the benchmark sampler");
        Batch batch(contextSize);
        batch.value.n_tokens = promptTokens;
        for (int i = 0; i < promptTokens; ++i) {
            batch.value.token[i] = tokens[i];
            batch.value.pos[i] = i;
            batch.value.n_seq_id[i] = 1;
            batch.value.seq_id[i][0] = 0;
            batch.value.logits[i] = (i == promptTokens - 1);
        }

        int outputTokens = 0;
        jlong firstToken = 0;
        Clock::time_point firstTokenTime;
        Clock::time_point lastTokenTime;
        while (outputTokens < maxTokens && !aborted(flag)) {
            if (llama_decode(context.get(), batch.value) != 0) {
                if (aborted(flag)) break;
                throw std::runtime_error("Model decode failed");
            }
            if (aborted(flag)) break;
            const llama_token token = llama_sampler_sample(sampler.get(), context.get(), -1);
            if (llama_vocab_is_eog(vocabulary, token)) break;
            lastTokenTime = Clock::now();
            if (outputTokens == 0) {
                firstToken = elapsed(start);
                firstTokenTime = lastTokenTime;
            }
            batch.value.n_tokens = 1;
            batch.value.token[0] = token;
            batch.value.pos[0] = promptTokens + outputTokens;
            batch.value.logits[0] = true;
            ++outputTokens;
        }
        const jlong decodeNanos = outputTokens > 1
            ? std::chrono::duration_cast<std::chrono::nanoseconds>(lastTokenTime - firstTokenTime).count()
            : 0;
        return result(env, promptTokens, outputTokens, firstToken, decodeNanos, aborted(flag));
    } catch (const std::exception& error) {
        throwJava(env, error.what());
        return nullptr;
    }
}
