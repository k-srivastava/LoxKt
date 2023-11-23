package lox

import runtimeError

class Interpreter : Expression.Visitor<Any>, Statement.Visitor<Void?> {
    val globals = Environment()
    private val locals: MutableMap<Expression, Int> = HashMap()
    private var environment = globals

    init {
        globals.define("clock", object : LoxCallable {
            override fun arity(): Int {
                return 0
            }

            override fun call(interpreter: Interpreter, arguments: List<Any>): Any {
                return System.currentTimeMillis() / 1000.0
            }

            override fun toString(): String {
                return "<native fn>"
            }
        })
    }

    fun interpret(statements: List<Statement>) {
        try {
            for (statement in statements) {
                execute(statement)
            }
        }
        catch (error: RuntimeError) {
            runtimeError(error)
        }
    }

    private fun evaluate(expression: Expression): Any? {
        return expression.accept(this)
    }

    private fun execute(statement: Statement) {
        statement.accept(this)
    }

    fun executeBlock(statements: List<Statement>, environment: Environment) {
        val previous = this.environment

        try {
            this.environment = environment
            for (statement in statements) {
                execute(statement)
            }
        }
        finally {
            this.environment = previous
        }
    }

    fun resolve(expression: Expression, depth: Int) {
        locals[expression] = depth
    }

    fun lookupVariable(name: Token, expression: Expression): Any? {
        val distance = locals[expression]

        if (distance != null) return environment.getAt(distance, name.lexeme)
        return globals.get(name)
    }

    override fun visitAssignExpr(expression: Expression.Assign): Any? {
        val value = evaluate(expression.value)
        val distance = locals[expression.value]

        if (distance != null) environment.assignAt(distance, expression.name, value)
        else globals.assign(expression.name, value)

        return value
    }

    override fun visitBinaryExpr(expression: Expression.Binary): Any {
        val left = evaluate(expression.left)
        val right = evaluate(expression.right)

        return when (expression.operator.type) {
            TokenType.GREATER_THAN -> {
                checkNumberOperands(expression.operator, left, right)
                left as Double > right as Double
            }

            TokenType.GREATER_THAN_EQUALS -> {
                checkNumberOperands(expression.operator, left, right)
                left as Double >= right as Double
            }

            TokenType.LESSER_THAN -> {
                checkNumberOperands(expression.operator, left, right)
                !(left as Double >= right as Double)
            }

            TokenType.LESSER_THAN_EQUALS -> {
                checkNumberOperands(expression.operator, left, right)
                left as Double <= right as Double
            }

            TokenType.BANG_EQUALS -> !isEqual(left, right)
            TokenType.EQUAL_EQUALS -> isEqual(left, right)

            TokenType.MINUS -> {
                checkNumberOperands(expression.operator, left, right)
                left as Double - right as Double
            }

            TokenType.PLUS -> {
                if (left is Double && right is Double) left + right
                else if (left is String && right is String) left + right
                else throw RuntimeError(expression.operator, "Operands must be two numbers or two strings.")
            }

            TokenType.SLASH -> {
                checkNumberOperands(expression.operator, left, right)
                left as Double / right as Double
            }

            TokenType.ASTERISK -> {
                checkNumberOperands(expression.operator, left, right)
                left as Double * right as Double
            }

            else -> {}
        }
    }

    override fun visitCallExpr(expression: Expression.Call): Any? {
        val callee = evaluate(expression.callee)

        val arguments: List<Any> = ArrayList()
        for (argument in expression.arguments) arguments.addLast(evaluate(argument))

        if (callee !is LoxCallable) throw RuntimeError(expression.parenthesis, "Can only call functions and classes.")

        if (arguments.size != callee.arity()) throw RuntimeError(
            expression.parenthesis,
            "Expected ${callee.arity()} arguments but got ${arguments.size}."
        )

        return callee.call(this, arguments)
    }

    override fun visitGetExpr(expression: Expression.Get): Any? {
        val `object` = evaluate(expression.`object`)
        if (`object` is LoxInstance) return `object`.get(expression.name)

        throw RuntimeError(expression.name, "Only instances have properties.")
    }

    override fun visitGroupingExpr(expression: Expression.Grouping): Any? {
        return evaluate(expression.expression)
    }

    override fun visitLiteralExpr(expression: Expression.Literal): Any? {
        return expression.value
    }

    override fun visitLogicalExpr(expression: Expression.Logical): Any? {
        val left = evaluate(expression.left)

        if (expression.operator.type == TokenType.OR) {
            if (isTruthy(left)) return left
        }
        else {
            if (!isTruthy(left)) return left
        }

        return evaluate(expression.right)
    }

    override fun visitSetExpr(expression: Expression.Set): Any? {
        val `object` = evaluate(expression.`object`)
        if (`object` !is LoxInstance) throw RuntimeError(expression.name, "Only instances have fields.")

        val value = evaluate(expression.value)
        `object`.set(expression.name, value)

        return value
    }

    override fun visitSuperExpr(expression: Expression.Super): Any {
        val distance = locals[expression]!!
        val superclass = environment.getAt(distance, "super") as LoxClass
        val `object` = environment.getAt(distance - 1, "this") as LoxInstance

        val method = superclass.findMethod(expression.method.lexeme)
            ?: throw RuntimeError(expression.method, "Undefined property '${expression.method.lexeme}'.")

        return method.bind(`object`)
    }

    override fun visitThisExpr(expression: Expression.This): Any? {
        return lookupVariable(expression.keyword, expression)
    }

    override fun visitUnaryExpr(expression: Expression.Unary): Any {
        val right = evaluate(expression.right)

        return when (expression.operator.type) {
            TokenType.BANG -> !isTruthy(right)
            TokenType.MINUS -> {
                checkNumberOperand(expression.operator, right)
                return -(right as Double)
            }

            else -> {}
        }
    }

    override fun visitVariableExpr(expression: Expression.Variable): Any? {
        return lookupVariable(expression.name, expression)
    }

    override fun visitBlockStatement(statement: Statement.Block): Void? {
        executeBlock(statement.statements, Environment(environment))
        return null
    }

    override fun visitClassStatement(statement: Statement.Class): Void? {
        var superClass: Any? = null

        if (statement.superClass != null) {
            superClass = evaluate(statement.superClass)
            if (superClass !is LoxClass) throw RuntimeError(statement.superClass.name, "Superclass must be a class.")
        }

        environment.define(statement.name.lexeme, null)

        if (statement.superClass != null) {
            environment = Environment(environment)
            environment.define("super", superClass)
        }

        val methods: MutableMap<String, LoxFunction> = HashMap()
        for (method in statement.methods) {
            val function = LoxFunction(method, environment, method.name.lexeme == "init")
            methods[method.name.lexeme] = function
        }

        val loxSuperClass = if (superClass != null) superClass as LoxClass
        else null

        val `class` = LoxClass(statement.name.lexeme, loxSuperClass, methods)

        if (superClass != null) environment = environment.enclosing!!

        environment.assign(statement.name, `class`)

        return null
    }

    override fun visitExpressionStatement(statement: Statement.Expression): Void? {
        evaluate(statement.expression)
        return null
    }

    override fun visitFunctionStatement(statement: Statement.Function): Void? {
        val function = LoxFunction(statement, environment, false)
        environment.define(statement.name.lexeme, function)

        return null
    }

    override fun visitIfStatement(statement: Statement.If): Void? {
        if (isTruthy(evaluate(statement.condition))) execute(statement.thenBranch)
        else if (statement.elseBranch != null) execute(statement.elseBranch)

        return null
    }

    override fun visitPrintStatement(statement: Statement.Print): Void? {
        val value = evaluate(statement.expression)
        println(stringify(value))
        return null
    }

    override fun visitReturnStatement(statement: Statement.Return): Void? {
        val value = if (statement.value != null) evaluate(statement.value)
        else null

        throw Return(value)
    }

    override fun visitVarStatement(statement: Statement.Var): Void? {
        var value: Any? = null

        if (statement.initializer != null) value = evaluate(statement.initializer)
        environment.define(statement.name.lexeme, value)

        return null
    }

    override fun visitWhileStatement(statement: Statement.While): Void? {
        while (isTruthy(evaluate(statement.condition))) execute(statement.body)
        return null
    }

    private fun isTruthy(`object`: Any?): Boolean {
        if (`object` == null) return false
        return if (`object` is Boolean) `object` else true
    }

    private fun isEqual(a: Any?, b: Any?): Boolean {
        if (a == null && b == null) return true
        return if (a == null) false else a == b
    }

    private fun stringify(`object`: Any?): String {
        if (`object` == null) return "nil"

        if (`object` is Double) {
            var text = `object`.toString()

            if (text.endsWith(".0")) text = text.substring(0, text.length - 2)
            return text
        }

        return `object`.toString()
    }

    private fun checkNumberOperand(operator: Token, operand: Any?) {
        if (operand is Double) return
        throw RuntimeError(operator, "Operand must be a number.")
    }

    private fun checkNumberOperands(operator: Token, left: Any?, right: Any?) {
        if (left is Double && right is Double) return
        throw RuntimeError(operator, "Operands must be numbers.")
    }
}