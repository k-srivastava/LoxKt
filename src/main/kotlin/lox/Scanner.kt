package lox

import reportError


class Scanner(private val source: String) {
    private var start = 0
    private var current = 0
    private var line = 1u

    private val keywords: Map<String, TokenType> = mapOf(
        Pair("and", TokenType.AND),
        Pair("class", TokenType.CLASS),
        Pair("else", TokenType.ELSE),
        Pair("false", TokenType.FALSE),
        Pair("for", TokenType.FOR),
        Pair("fun", TokenType.FUN),
        Pair("if", TokenType.IF),
        Pair("nil", TokenType.NIL),
        Pair("or", TokenType.OR),
        Pair("print", TokenType.PRINT),
        Pair("return", TokenType.RETURN),
        Pair("super", TokenType.SUPER),
        Pair("this", TokenType.THIS),
        Pair("true", TokenType.TRUE),
        Pair("var", TokenType.VAR),
        Pair("while", TokenType.WHILE)
    )

    private val tokens: MutableList<Token> = ArrayList()

    fun scanTokens(): List<Token> {
        while (!isAtEnd()) {
            start = current
            scanToken()
        }

        tokens.add(Token(TokenType.EOF, "", null, line))
        return tokens
    }

    private fun scanToken() {
        when (val char = advance()) {
            '(' -> addToken(TokenType.LEFT_PARENTHESIS)
            ')' -> addToken(TokenType.RIGHT_PARENTHESIS)
            '{' -> addToken(TokenType.LEFT_BRACE)
            '}' -> addToken(TokenType.RIGHT_BRACE)
            ',' -> addToken(TokenType.COMMA)
            '.' -> addToken(TokenType.DOT)
            '-' -> addToken(TokenType.MINUS)
            '+' -> addToken(TokenType.PLUS)
            ';' -> addToken(TokenType.SEMICOLON)
            '*' -> addToken(TokenType.ASTERISK)

            '!' -> addToken(if (match()) TokenType.BANG_EQUALS else TokenType.BANG)
            '=' -> addToken(if (match()) TokenType.EQUAL_EQUALS else TokenType.EQUAL)
            '<' -> addToken(if (match()) TokenType.LESSER_THAN_EQUALS else TokenType.LESSER_THAN)
            '>' -> addToken(if (match()) TokenType.GREATER_THAN_EQUALS else TokenType.GREATER_THAN)

            '/' -> {
                if (match('/')) {
                    while (peek() != '\n' && !isAtEnd()) advance()
                } else addToken(TokenType.SLASH)
            }

            ' ', '\r', '\t' -> {}
            '\n' -> line++

            '"' -> scanString()

            else -> {
                if (char.isDigit()) scanNumber()
                else if (char.isLetter()) scanIdentifier()
                else reportError(line, message = "Unexpected character.")
            }
        }

    }

    private fun scanString() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++
            advance()
        }

        if (isAtEnd()) {
            reportError(line, message = "Unterminated string.")
            return
        }

        advance()

        val value = source.substring(start + 1, current - 1)
        addToken(TokenType.STRING, value)
    }

    private fun scanNumber() {
        while (peek().isDigit()) advance()

        if (peek() == '.' && peekNext().isDigit()) {
            advance()
            while (peek().isDigit()) advance()
        }

        addToken(TokenType.NUMBER, source.substring(start, current).toDouble())
    }

    private fun scanIdentifier() {
        while (peek().isLetterOrDigit()) advance()

        val text = source.substring(start, current)
        val type = keywords.getOrDefault(text, TokenType.IDENTIFIER)

        addToken(type)
    }

    private fun addToken(type: TokenType, literal: Any? = null) {
        val text = source.substring(start, current)
        tokens.add(Token(type, text, literal, line))
    }

    private fun advance(): Char {
        return source[current++]
    }

    private fun peek(): Char {
        return if (isAtEnd()) '\u0000' else source[current]
    }

    private fun peekNext(): Char {
        return if (current + 1 >= source.length) '\u0000' else source[current + 1]
    }

    private fun isAtEnd(): Boolean {
        return current >= source.length
    }

    private fun match(expected: Char = '='): Boolean {
        if (isAtEnd()) return false
        if (source[current] != expected) return false

        current++
        return true
    }
}