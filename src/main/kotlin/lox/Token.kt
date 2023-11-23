package lox

data class Token(val type: TokenType, val lexeme: String, val literal: Any?, val line: UInt) {
    override fun toString(): String {
        return "$type $lexeme $literal"
    }
}
