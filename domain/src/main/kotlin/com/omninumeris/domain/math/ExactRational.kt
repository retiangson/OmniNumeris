package com.omninumeris.domain.math

import java.math.BigDecimal
import java.math.BigInteger

class ExactRational private constructor(
    val numerator: BigInteger,
    val denominator: BigInteger,
) {
    val isZero: Boolean get() = numerator.signum() == 0

    internal operator fun unaryMinus(): ExactRational = ExactRational(numerator.negate(), denominator)

    internal operator fun plus(other: ExactRational): ExactRational =
        normalized(numerator * other.denominator + other.numerator * denominator, denominator * other.denominator)

    internal operator fun minus(other: ExactRational): ExactRational = this + -other

    internal operator fun times(other: ExactRational): ExactRational =
        normalized(numerator * other.numerator, denominator * other.denominator)

    internal operator fun div(other: ExactRational): ExactRational =
        normalized(numerator * other.denominator, denominator * other.numerator)

    internal fun raisedTo(exponent: ExactRational): ExactRational {
        if (exponent.denominator != BigInteger.ONE || exponent.numerator.abs() > MAX_EXPONENT) {
            reject(MathErrorCode.Unsupported, "Only integer powers from −32 to 32 are supported")
        }
        val power = exponent.numerator.toInt()
        if (isZero && power == 0) reject(MathErrorCode.RealDomain, "Zero to the power of zero is undefined")
        if (isZero && power < 0) reject(MathErrorCode.DivisionByZero, "A negative power of zero is undefined")
        if (power < 0) return normalized(denominator.pow(-power), numerator.pow(-power))
        return normalized(numerator.pow(power), denominator.pow(power))
    }

    internal fun squareRoot(): ExactRational {
        if (numerator.signum() < 0) reject(MathErrorCode.RealDomain, "Square root requires a nonnegative operand")
        val top = integerSquareRoot(numerator)
        val bottom = integerSquareRoot(denominator)
        if (top * top != numerator || bottom * bottom != denominator) {
            reject(MathErrorCode.Unsupported, "This square root has no exact rational result")
        }
        return normalized(top, bottom)
    }

    override fun toString(): String =
        if (denominator == BigInteger.ONE) numerator.toString() else "$numerator/$denominator"

    override fun equals(other: Any?): Boolean =
        other is ExactRational && numerator == other.numerator && denominator == other.denominator

    override fun hashCode(): Int = 31 * numerator.hashCode() + denominator.hashCode()

    companion object {
        internal val ZERO = ExactRational(BigInteger.ZERO, BigInteger.ONE)
        internal val ONE = ExactRational(BigInteger.ONE, BigInteger.ONE)
        private val MAX_EXPONENT = BigInteger.valueOf(32)
        private const val MAX_BITS = 4_096

        internal fun literal(text: String): ExactRational {
            val decimal = BigDecimal(text)
            return normalized(decimal.unscaledValue(), BigInteger.TEN.pow(decimal.scale()))
        }

        private fun normalized(top: BigInteger, bottom: BigInteger): ExactRational {
            if (bottom.signum() == 0) reject(MathErrorCode.DivisionByZero, "Cannot divide by zero")
            val divisor = top.gcd(bottom)
            val signedDivisor = if (bottom.signum() < 0) divisor.negate() else divisor
            val numerator = top / signedDivisor
            val denominator = bottom / signedDivisor
            if (numerator.bitLength() > MAX_BITS || denominator.bitLength() > MAX_BITS) {
                reject(MathErrorCode.ComplexityLimit, "Exact result exceeds the numeric budget")
            }
            return ExactRational(numerator, denominator)
        }

        private fun integerSquareRoot(value: BigInteger): BigInteger {
            if (value <= BigInteger.ONE) return value
            var estimate = BigInteger.ONE.shiftLeft((value.bitLength() + 1) / 2)
            while (true) {
                val next = (estimate + value / estimate).shiftRight(1)
                if (next >= estimate) return estimate
                estimate = next
            }
        }
    }
}

internal fun reject(code: MathErrorCode, detail: String): Nothing = throw MathRejection(MathError(code, detail))
