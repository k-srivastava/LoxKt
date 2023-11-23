package lox

abstract class Expression {
    interface Visitor<R> {
        fun visitAssignExpr(expression: Assign): R?
        fun visitBinaryExpr(expression: Binary): R
        fun visitCallExpr(expression: Call): R?
        fun visitGetExpr(expression: Get): R?
        fun visitGroupingExpr(expression: Grouping): R?
        fun visitLiteralExpr(expression: Literal): R?
        fun visitLogicalExpr(expression: Logical): R?
        fun visitSetExpr(expression: Set): R?
        fun visitSuperExpr(expression: Super): R
        fun visitThisExpr(expression: This): R?
        fun visitUnaryExpr(expression: Unary): R
        fun visitVariableExpr(expression: Variable): R?
    }

    class Assign(val name: Token, val value: Expression) : Expression() {
        override fun <R> accept(visitor: Visitor<R>): R? {
            return visitor.visitAssignExpr(this)
        }
    }

    class Binary(val left: Expression, val operator: Token, val right: Expression) : Expression() {
        override fun <R> accept(visitor: Visitor<R>): R {
            return visitor.visitBinaryExpr(this)
        }
    }

    class Call(val callee: Expression, val parenthesis: Token, val arguments: List<Expression>) : Expression() {
        override fun <R> accept(visitor: Visitor<R>): R? {
            return visitor.visitCallExpr(this)
        }
    }

    class Get(val `object`: Expression, val name: Token) : Expression() {
        override fun <R> accept(visitor: Visitor<R>): R? {
            return visitor.visitGetExpr(this)
        }
    }

    class Grouping(val expression: Expression) : Expression() {
        override fun <R> accept(visitor: Visitor<R>): R? {
            return visitor.visitGroupingExpr(this)
        }
    }

    class Literal(val value: Any?) : Expression() {
        override fun <R> accept(visitor: Visitor<R>): R? {
            return visitor.visitLiteralExpr(this)
        }
    }

    class Logical(val left: Expression, val operator: Token, val right: Expression) : Expression() {
        override fun <R> accept(visitor: Visitor<R>): R? {
            return visitor.visitLogicalExpr(this)
        }
    }

    class Set(val `object`: Expression, val name: Token, val value: Expression) : Expression() {
        override fun <R> accept(visitor: Visitor<R>): R? {
            return visitor.visitSetExpr(this)
        }
    }

    class Super(val keyword: Token, val method: Token): Expression() {
        override fun <R> accept(visitor: Visitor<R>): R {
            return visitor.visitSuperExpr(this)
        }
    }

    class This(val keyword: Token) : Expression() {
        override fun <R> accept(visitor: Visitor<R>): R? {
            return visitor.visitThisExpr(this)
        }
    }

    class Unary(val operator: Token, val right: Expression) : Expression() {
        override fun <R> accept(visitor: Visitor<R>): R {
            return visitor.visitUnaryExpr(this)
        }
    }

    class Variable(val name: Token) : Expression() {
        override fun <R> accept(visitor: Visitor<R>): R? {
            return visitor.visitVariableExpr(this)
        }
    }

    abstract fun <R> accept(visitor: Visitor<R>): R?
}