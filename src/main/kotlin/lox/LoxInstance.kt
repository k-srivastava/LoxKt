package lox

class LoxInstance(private val `class`: LoxClass) {
    private val fields: MutableMap<String, Any?> = HashMap()

    fun get(name: Token): Any? {
        if (fields.containsKey(name.lexeme)) return fields[name.lexeme]

        val method = `class`.findMethod(name.lexeme)
        if (method != null) return method.bind(this)

        throw RuntimeError(name, "Undefined property '${name.lexeme}'.")
    }

    fun set(name: Token, value: Any?) {
        fields[name.lexeme] = value
    }

    override fun toString(): String {
        return `class`.name + " instance"
    }
}