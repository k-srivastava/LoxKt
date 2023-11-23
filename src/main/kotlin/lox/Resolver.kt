package lox

import generateError
import java.util.*

private enum class FunctionType {
    NONE,
    FUNCTION,
    INITIALIZER,
    METHOD
}

private enum class ClassType {
    NONE,
    CLASS,
    SUBCLASS
}

class Resolver(private val interpreter: Interpreter) : Expression.Visitor<Void?>, Statement.Visitor<Void?> {
    private val scopes: Stack<MutableMap<String, Boolean>> = Stack()
    private var currentFunction = FunctionType.NONE
    private var currentClass = ClassType.NONE

    fun resolve(statements: List<Statement>) {
        for (statement in statements) resolve(statement)
    }

    override fun visitAssignExpr(expression: Expression.Assign): Void? {
        resolve(expression.value)
        resolveLocal(expression, expression.name)

        return null
    }

    override fun visitBinaryExpr(expression: Expression.Binary): Void? {
        resolve(expression.left)
        resolve(expression.right)
        return null
    }

    override fun visitCallExpr(expression: Expression.Call): Void? {
        resolve(expression.callee)
        for (argument in expression.arguments) resolve(argument)

        return null
    }

    override fun visitGetExpr(expression: Expression.Get): Void? {
        resolve(expression.`object`)
        return null
    }

    override fun visitGroupingExpr(expression: Expression.Grouping): Void? {
        resolve(expression.expression)
        return null
    }

    override fun visitLiteralExpr(expression: Expression.Literal): Void? {
        return null
    }

    override fun visitLogicalExpr(expression: Expression.Logical): Void? {
        resolve(expression.left)
        resolve(expression.right)
        return null
    }

    override fun visitSetExpr(expression: Expression.Set): Void? {
        resolve(expression.value)
        resolve(expression.`object`)

        return null
    }

    override fun visitSuperExpr(expression: Expression.Super): Void? {
        if (currentClass == ClassType.NONE) generateError(expression.keyword, "Cannot use 'super' outside of a class.")

        if (currentClass != ClassType.SUBCLASS) {
            generateError(expression.keyword, "Cannot use 'super' in a class with no super class.")
        }

        resolveLocal(expression, expression.keyword)
        return null
    }

    override fun visitThisExpr(expression: Expression.This): Void? {
        if (currentClass == ClassType.NONE) generateError(expression.keyword, "Cannot use 'this' outside of a class.")

        resolveLocal(expression, expression.keyword)
        return null
    }

    override fun visitUnaryExpr(expression: Expression.Unary): Void? {
        resolve(expression.right)
        return null
    }

    override fun visitVariableExpr(expression: Expression.Variable): Void? {
        if (!scopes.isEmpty() && scopes.peek()[expression.name.lexeme] == false)
            generateError(expression.name, "Cannot read local variable in its own initializer.")

        resolveLocal(expression, expression.name)
        return null
    }

    override fun visitBlockStatement(statement: Statement.Block): Void? {
        beginScope()
        resolve(statement.statements)
        endScope()

        return null
    }

    override fun visitClassStatement(statement: Statement.Class): Void? {
        val enclosingClass = currentClass
        currentClass = ClassType.CLASS

        declare(statement.name)
        define(statement.name)

        if (statement.superClass != null && statement.name.lexeme == statement.superClass.name.lexeme) {
            generateError(statement.superClass.name, "A class cannot inherit from itself.")
        }

        if (statement.superClass != null) {
            currentClass = ClassType.SUBCLASS
            resolve(statement.superClass)
        }

        if (statement.superClass != null) {
            beginScope()
            scopes.peek()["super"] = true
        }

        beginScope()
        scopes.peek()["this"] = true

        for (method in statement.methods) {
            val declaration = if (method.name.lexeme == "init") FunctionType.INITIALIZER
            else FunctionType.METHOD

            resolveFunction(method, declaration)
        }

        endScope()
        if (statement.superClass != null) endScope()

        currentClass = enclosingClass

        return null
    }

    override fun visitExpressionStatement(statement: Statement.Expression): Void? {
        resolve(statement.expression)
        return null
    }

    override fun visitFunctionStatement(statement: Statement.Function): Void? {
        declare(statement.name)
        declare(statement.name)
        resolveFunction(statement, FunctionType.FUNCTION)

        return null
    }

    override fun visitIfStatement(statement: Statement.If): Void? {
        resolve(statement.condition)
        resolve(statement.thenBranch)
        statement.elseBranch?.let { resolve(it) }

        return null
    }

    override fun visitPrintStatement(statement: Statement.Print): Void? {
        resolve(statement.expression)
        return null
    }

    override fun visitReturnStatement(statement: Statement.Return): Void? {
        if (currentFunction == FunctionType.NONE) generateError(statement.keyword, "Cannot return from top-level code.")

        if (statement.value != null) {
            if (currentFunction == FunctionType.INITIALIZER) {
                generateError(statement.keyword, "Cannot return a value from an initializer.")
            }

            resolve(statement.value)
        }

        return null
    }

    override fun visitVarStatement(statement: Statement.Var): Void? {
        declare(statement.name)
        statement.initializer?.let { resolve(it) }
        define(statement.name)

        return null
    }

    override fun visitWhileStatement(statement: Statement.While): Void? {
        resolve(statement.condition)
        resolve(statement.body)
        return null
    }

    private fun resolve(expression: Expression) {
        expression.accept(this)
    }

    private fun resolve(statement: Statement) {
        statement.accept(this)
    }

    private fun resolveLocal(expr: Expression, name: Token) {
        for (i in scopes.indices.reversed()) {
            if (scopes[i].containsKey(name.lexeme)) {
                interpreter.resolve(expr, scopes.size - 1 - i)
                return
            }
        }
    }

    private fun resolveFunction(function: Statement.Function, type: FunctionType) {
        val enclosingFunction = currentFunction
        currentFunction = type
        beginScope()

        for (parameter in function.parameters) {
            declare(parameter)
            define(parameter)
        }

        resolve(function.body)

        endScope()
        currentFunction = enclosingFunction
    }

    private fun beginScope() {
        scopes.push(HashMap())
    }

    private fun endScope() {
        scopes.pop()
    }

    private fun declare(name: Token) {
        if (scopes.isEmpty()) return

        val scope = scopes.peek()
        if (scope.containsKey(name.lexeme)) {
            generateError(name, "A variable with this name already exists in the scope.")
        }

        scope[name.lexeme] = false
    }

    private fun define(name: Token) {
        if (scopes.isEmpty()) return
        scopes.peek()[name.lexeme] = true
    }
}