package com.omninumeris.domain.math

object MathParser {
    fun parse(text: String): MathParseResult {
        if (text.length > MAX_INPUT_LENGTH) {
            return MathParseResult.Rejected(MathError(MathErrorCode.ComplexityLimit, "Expression exceeds the input limit"))
        }
        return try {
            MathParseResult.Parsed(Reader(text).parse())
        } catch (rejected: MathRejection) {
            MathParseResult.Rejected(rejected.error)
        }
    }

    private class Reader(private val source: String) {
        private var offset = 0

        fun parse(): MathInput {
            val left = sum(0)
            val input = if (accept('=')) MathInput.Equation(left, sum(0)) else MathInput.Expression(left)
            whitespace()
            if (offset < source.length) {
                if (source[offset] == '\\') fail(MathErrorCode.Unsupported, "Unsupported LaTeX command")
                fail(MathErrorCode.Syntax, "Unexpected character. Insert an explicit operator between terms")
            }
            return input
        }

        private fun sum(depth: Int): MathExpression {
            var current = product(depth)
            while (true) {
                val operation = when {
                    accept('+') -> MathOperator.Add
                    accept('-', '−') -> MathOperator.Subtract
                    else -> return current
                }
                current = MathExpression.Binary(operation, current, product(depth))
            }
        }

        private fun product(depth: Int): MathExpression {
            var current = unary(depth)
            while (true) {
                val operation = when {
                    accept('*', '×') || acceptCommand("times") || acceptCommand("cdot") -> MathOperator.Multiply
                    accept('/', '÷') || acceptCommand("div") -> MathOperator.Divide
                    else -> return current
                }
                current = MathExpression.Binary(operation, current, unary(depth))
            }
        }

        private fun unary(depth: Int): MathExpression {
            if (depth > MAX_NESTING) fail(MathErrorCode.ComplexityLimit, "Expression exceeds the nesting limit")
            return when {
                accept('-', '−') -> MathExpression.Negate(unary(depth + 1))
                accept('+') -> unary(depth + 1)
                else -> {
                    val base = atom(depth)
                    if (accept('^')) {
                        val exponent = if (accept('{')) grouped(depth + 1, '}') else unary(depth + 1)
                        MathExpression.Binary(MathOperator.Power, base, exponent)
                    } else base
                }
            }
        }

        private fun atom(depth: Int): MathExpression {
            if (accept('(')) return grouped(depth, ')')
            if (acceptCommand("frac")) {
                val numerator = latexArgument(depth, "numerator")
                val denominator = latexArgument(depth, "denominator")
                return MathExpression.Binary(MathOperator.Divide, numerator, denominator)
            }
            if (acceptCommand("sqrt")) {
                whitespace()
                if (source.getOrNull(offset) == '[') {
                    fail(MathErrorCode.Unsupported, "Only square roots are supported")
                }
                return MathExpression.SquareRoot(latexArgument(depth, "square-root operand"))
            }
            whitespace()
            if (accept('√') || namedRoot()) {
                if (!accept('(')) fail(MathErrorCode.Syntax, "Wrap the square-root operand in parentheses")
                return MathExpression.SquareRoot(grouped(depth, ')'))
            }
            if (accept('x')) return MathExpression.Variable
            val start = offset
            var sawDigit = digits()
            if (source.getOrNull(offset) == '.') {
                offset++
                sawDigit = digits() || sawDigit
            }
            if (sawDigit) {
                val literal = source.substring(start, offset)
                if (literal.length > MAX_LITERAL_LENGTH) {
                    fail(MathErrorCode.ComplexityLimit, "Numeric literal exceeds the digit limit")
                }
                return MathExpression.Number(literal)
            }
            if (source.getOrNull(offset)?.isLetter() == true || source.getOrNull(offset) == '\\') {
                fail(MathErrorCode.Unsupported, "Use numbers, x, or a supported command such as \\frac or \\sqrt")
            }
            fail(MathErrorCode.Syntax, "Expected a number, x, a square root, or an opening parenthesis")
        }

        private fun latexArgument(depth: Int, name: String): MathExpression {
            if (!accept('{')) fail(MathErrorCode.Syntax, "Put the $name inside braces")
            return grouped(depth, '}')
        }

        private fun grouped(depth: Int, closing: Char): MathExpression {
            val inner = sum(depth + 1)
            if (!accept(closing)) {
                val delimiter = if (closing == '}') "brace" else "parenthesis"
                fail(MathErrorCode.Syntax, "Expected a closing $delimiter")
            }
            return inner
        }

        private fun acceptCommand(command: String): Boolean {
            whitespace()
            if (source.getOrNull(offset) != '\\' || !source.startsWith(command, offset + 1)) return false
            val end = offset + 1 + command.length
            if (source.getOrNull(end)?.isLetter() == true) return false
            offset = end
            return true
        }

        private fun digits(): Boolean {
            val start = offset
            while (source.getOrNull(offset)?.let { it in '0'..'9' } == true) offset++
            return offset != start
        }

        private fun namedRoot(): Boolean {
            if (!source.startsWith("sqrt", offset)) return false
            offset += 4
            return true
        }

        private fun accept(vararg choices: Char): Boolean {
            whitespace()
            val next = source.getOrNull(offset) ?: return false
            if (next !in choices) return false
            offset++
            return true
        }

        private fun whitespace() {
            while (source.getOrNull(offset)?.isWhitespace() == true) offset++
        }

        private fun fail(code: MathErrorCode, detail: String): Nothing =
            throw MathRejection(MathError(code, detail, offset))
    }

    const val MAX_INPUT_LENGTH = 512
    private const val MAX_NESTING = 32
    const val MAX_LITERAL_LENGTH = 64
}
