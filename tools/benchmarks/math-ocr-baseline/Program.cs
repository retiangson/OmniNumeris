using System.Diagnostics;
using System.Security.Cryptography;
using System.Text.Json;
using Tesseract;

const string ModelHash = "7D4322BD2A7749724879683FC3912CB542F19906C83BCC1A52132556427170B2";
if (args.Length != 2)
{
    Console.Error.WriteLine("Usage: MathOcrBaseline <corpus directory> <tessdata directory>");
    return 2;
}

var corpus = Path.GetFullPath(args[0]);
var data = Path.GetFullPath(args[1]);
using (var trainedData = File.OpenRead(Path.Combine(data, "eng.traineddata")))
{
    if (Convert.ToHexString(SHA256.HashData(trainedData)) != ModelHash)
    {
        Console.Error.WriteLine("English tessdata SHA-256 mismatch");
        return 2;
    }
}

using var engine = new TesseractEngine(data, "eng", EngineMode.LstmOnly);
foreach (var line in File.ReadLines(Path.Combine(corpus, "labels.csv")).Skip(1))
{
    var columns = line.Split(',');
    if (columns.Length != 5 || Path.GetFileName(columns[1]) != columns[1])
    {
        throw new InvalidDataException("Invalid fixture label row");
    }

    var id = columns[0];
    var expected = columns[3];
    using var image = Pix.LoadFromFile(Path.Combine(corpus, columns[1]));
    var (raw, first) = Recognize(engine, image);
    var warm = Enumerable.Range(0, 3).Select(_ => Recognize(engine, image).ElapsedMs).Order().ToArray();
    var normalized = Canonicalize(raw);
    Console.WriteLine(JsonSerializer.Serialize(new
    {
        id,
        source = columns[2],
        expected,
        raw = raw.Trim(),
        normalized,
        exact = normalized == expected,
        firstMs = first,
        warmMedianMs = warm[1],
    }));
}

return 0;

static (string Text, double ElapsedMs) Recognize(TesseractEngine engine, Pix image)
{
    var start = Stopwatch.GetTimestamp();
    using var page = engine.Process(image, PageSegMode.SingleBlock);
    var text = page.GetText();
    return (text, Stopwatch.GetElapsedTime(start).TotalMilliseconds);
}

static string Canonicalize(string text) =>
    string.Concat(text.Where(character => !char.IsWhiteSpace(character)))
        .Replace("×", "*")
        .Replace("÷", "/")
        .Replace("−", "-")
        .Replace("√", "sqrt");
