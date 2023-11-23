package lox

abstract class Statement {
    interface Visitor<R> {
        fun visitBlockStatement(statement: Block): R
        fun visitClassStatement(statement: Class): R
        fun visitExpressionStatement(statement: Expression): R
        fun visitFunctionStatement(statement: Function): R
        fun visitIfStatement(statement: If): R
        fun visitPrintStatement(statement: Print): R
        fun visitReturnStatement(statement: Return): R
        fun visitVarStatement(statement: Var): R
        fun visitWhileStatement(statement: While): R
    }

    class Block(val statements: List<Statement>) : Statement() {
        override fun <R> accept(visitor: Visitor<R>): R {
            return visitor.visitBlockStatement(this)
        }
    }

    class Class(val name: Token, val superClass: lox.Expression.Variable?, val methods: List<Function>) :
        Statement() {
        override fun <R> accept(visitor: Visitor<R>): R {
            return visitor.visitClassStatement(this)
        }
    }

    class Expression(val expression: lox.Expression) : Statement() {
        override fun <R> accept(visitor: Visitor<R>): R {
            return visitor.visitExpressionStatement(this)
        }
    }

    class Function(val name: Token, val parameters: List<Token>, val body: List<Statement>) : Statement() {
        override fun <R> accept(visitor: Visitor<R>): R {
            return visitor.visitFunctionStatement(this)
        }
    }

    class If(val condition: lox.Expression, val thenBranch: Statement, val elseBranch: Statement?) : Statement() {
        override fun <R> accept(visitor: Visitor<R>): R {
            return visitor.visitIfStatement(this)
        }
    }

    class Print(val expression: lox.Expression) : Statement() {
        override fun <R> accept(visitor: Visitor<R>): R {
            return visitor.visitPrintStatement(this)
        }
    }

    class Return(val keyword: Token, val value: lox.Expression?) : Statement() {
        override fun <R> accept(visitor: Visitor<R>): R {
            return visitor.visitReturnStatement(this)
        }
    }

    class Var(val name: Token, val initializer: lox.Expression?) : Statement() {
        override fun <R> accept(visitor: Visitor<R>): R {
            return visitor.visitVarStatement(this)
        }
    }

    class While(val condition: lox.Expression, val body: Statement) : Statement() {
        override fun <R> accept(visitor: Visitor<R>): R {
            return visitor.visitWhileStatement(this)
        }
    }

    abstract fun <R> accept(visitor: Visitor<R>): R
}