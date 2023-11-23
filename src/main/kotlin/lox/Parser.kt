package lox

import generateError


private class ParseError : RuntimeException()

class Parser(private val tokens: List<Token>) {
    private var current = 0

    fun parse(): List<Statement> {
        val statements: List<Statement> = ArrayList()
        while (!isAtEnd()) statements.addLast(declaration())
        return statements
    }

    private fun expression(): Expression {
        return assignment()
    }

    private fun statement(): Statement {
        if (match(TokenType.FUN)) return function("function")
        if (match((TokenType.IF))) return ifStatement()
        if (match(TokenType.PRINT)) return printStatement()
        if (match(TokenType.RETURN)) return returnStatement()
        if (match(TokenType.FOR)) return forStatement()
        if (match(TokenType.WHILE)) return whileStatement()
        if (match(TokenType.LEFT_BRACE)) return Statement.Block(block())

        return expressionStatement()
    }

    private fun declaration(): Statement? {
        try {
            if (match(TokenType.CLASS)) return classDeclaration()
            if (match(TokenType.FUN)) return function("function")
            if (match(TokenType.VAR)) return varDeclaration()
            return statement()
        }
        catch (error: ParseError) {
            synchronize()
            return null
        }
    }

    private fun block(): List<Statement> {
        val statements: List<Statement> = ArrayList()

        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) statements.addLast(declaration())
        consume(TokenType.RIGHT_BRACE, "Expected '}' after block.")

        return statements
    }

    private fun function(kind: String): Statement.Function {
        val name = consume(TokenType.IDENTIFIER, "Expected $kind name.")
        consume(TokenType.LEFT_PARENTHESIS, "Expected '(' after $kind name.")

        val parameters: List<Token> = ArrayList()
        if (!check(TokenType.RIGHT_PARENTHESIS)) {
            do {
                if (parameters.size >= 255) error(peek(), "Cannot have more than 255 parameters.")

                parameters.addLast(consume(TokenType.IDENTIFIER, "Expected parameter name."))
            }
            while (match(TokenType.COMMA))
        }

        consume(TokenType.RIGHT_PARENTHESIS, "Expected ')' after parameters.")
        consume(TokenType.LEFT_BRACE, "Expected '{' before $kind body.")

        val body = block()
        return Statement.Function(name, parameters, body)
    }


    private fun assignment(): Expression {
        val expression = or()

        if (match(TokenType.EQUAL)) {
            val equals = previous()
            val value = assignment()

            if (expression is Expression.Variable) {
                val name = expression.name
                return Expression.Assign(name, value)
            }
            else if (expression is Expression.Get) return Expression.Set(expression.`object`, expression.name, value)

            error(equals, "Invalid assignment target.")
        }

        return expression
    }

    private fun or(): Expression {
        var expression = and()

        while (match(TokenType.OR)) {
            val operator = previous()
            val right = and()
            expression = Expression.Logical(expression, operator, right)
        }

        return expression
    }

    private fun and(): Expression {
        var expression = equality()

        while (match(TokenType.AND)) {
            val operator = previous()
            val right = equality()
            expression = Expression.Logical(expression, operator, right)
        }

        return expression
    }

    private fun equality(): Expression {
        var expression = comparison()

        while (match(TokenType.BANG_EQUALS, TokenType.EQUAL_EQUALS)) {
            val operator = previous()
            val right = comparison()

            expression = Expression.Binary(expression, operator, right)
        }

        return expression
    }

    private fun comparison(): Expression {
        var expression = term()

        while (match(
                TokenType.GREATER_THAN,
                TokenType.GREATER_THAN_EQUALS,
                TokenType.LESSER_THAN,
                TokenType.LESSER_THAN_EQUALS
            )
        ) {
            val operator = previous()
            val right = term()
            expression = Expression.Binary(expression, operator, right)
        }

        return expression
    }

    private fun term(): Expression {
        var expression = factor()

        while (match(TokenType.MINUS, TokenType.PLUS)) {
            val operator = previous()
            val right = factor()
            expression = Expression.Binary(expression, operator, right)
        }

        return expression
    }

    private fun factor(): Expression {
        var expression = unary()

        while (match(TokenType.ASTERISK, TokenType.SLASH)) {
            val operator = previous()
            val right = unary()
            expression = Expression.Binary(expression, operator, right)
        }

        return expression
    }

    private fun unary(): Expression {
        if (match(TokenType.BANG, TokenType.MINUS)) {
            val operator = previous()
            val right = unary()
            return Expression.Unary(operator, right)
        }

        return call()
    }

    private fun call(): Expression {
        var expression = primary()

        while (true) {
            expression = if (match(TokenType.LEFT_PARENTHESIS)) finishCall(expression)
            else if (match(TokenType.DOT)) {
                val name = consume(TokenType.IDENTIFIER, "Expected property name after '.'.")
                Expression.Get(expression, name)
            }
            else break
        }

        return expression
    }

    private fun finishCall(callee: Expression): Expression {
        val arguments: List<Expression> = ArrayList()

        if (!check(TokenType.RIGHT_PARENTHESIS)) {
            do {
                if (arguments.size >= 255) error(peek(), "Cannot have more than 255 arguments.")
                arguments.addLast(expression())
            }
            while (match(TokenType.COMMA))
        }

        val parenthesis = consume(TokenType.RIGHT_PARENTHESIS, "Expected ')' after arguments.")
        return Expression.Call(callee, parenthesis, arguments)
    }

    private fun primary(): Expression {
        if (match(TokenType.FALSE)) return Expression.Literal(false)
        if (match(TokenType.TRUE)) return Expression.Literal(true)
        if (match(TokenType.NIL)) return Expression.Literal(null)

        if (match(TokenType.NUMBER, TokenType.STRING)) return Expression.Literal(previous().literal)

        if (match(TokenType.SUPER)) {
            val keyword = previous()
            consume(TokenType.DOT, "Expected '.' after 'super'.")
            val method = consume(TokenType.IDENTIFIER, "Expected superclass method name.")

            return Expression.Super(keyword, method)
        }

        if (match(TokenType.THIS)) return Expression.This(previous())

        if (match(TokenType.IDENTIFIER)) return Expression.Variable(previous())

        if (match(TokenType.LEFT_PARENTHESIS)) {
            val expression = expression()
            consume(TokenType.RIGHT_PARENTHESIS, "Expected ')' after expression.")
            return Expression.Grouping(expression)
        }

        throw error(peek(), "Expected expression.")
    }

    private fun expressionStatement(): Statement {
        val expression = expression()
        consume(TokenType.SEMICOLON, "Expected ';' after expression.")
        return Statement.Expression(expression)
    }

    private fun ifStatement(): Statement {
        consume(TokenType.LEFT_PARENTHESIS, "Expected '(' after 'if'.")
        val condition = expression()

        consume(TokenType.RIGHT_PARENTHESIS, "Expected ')' after if condition.")
        val thenBranch = statement()

        var elseBranch: Statement? = null
        if (match(TokenType.ELSE)) elseBranch = statement()

        return Statement.If(condition, thenBranch, elseBranch)
    }

    private fun printStatement(): Statement {
        val value = expression()
        consume(TokenType.SEMICOLON, "Expected ';' after value.")
        return Statement.Print(value)
    }

    private fun whileStatement(): Statement {
        consume(TokenType.LEFT_PARENTHESIS, "Expected '(' after 'while'.")
        val condition = expression()

        consume(TokenType.RIGHT_PARENTHESIS, "Expected ')' after while condition.")
        val body = statement()

        return Statement.While(condition, body)
    }

    private fun forStatement(): Statement {
        consume(TokenType.LEFT_PARENTHESIS, "Expected '(' after 'for'.")

        val initializer = if (match(TokenType.SEMICOLON)) null
        else if (match(TokenType.VAR)) varDeclaration()
        else expressionStatement()

        var condition = if (!check(TokenType.SEMICOLON)) expression()
        else null

        consume(TokenType.SEMICOLON, "Expected ';' after loop declaration.")

        val increment = if (!check(TokenType.RIGHT_PARENTHESIS)) expression()
        else null

        consume(TokenType.RIGHT_PARENTHESIS, "Expected ')' after for clauses.")

        var body = statement()
        if (increment != null) body = Statement.Block(listOf(body, Statement.Expression(increment)))
        if (condition == null) condition = Expression.Literal(true)

        body = Statement.While(condition, body)
        if (initializer != null) body = Statement.Block(listOf(initializer, body))

        return body
    }

    private fun returnStatement(): Statement {
        val keyword = previous()
        val value = if (!check(TokenType.SEMICOLON)) expression()
        else null

        consume(TokenType.SEMICOLON, "Expected ';' after return value.")
        return Statement.Return(keyword, value)
    }

    private fun classDeclaration(): Statement {
        val name = consume(TokenType.IDENTIFIER, "Expected a class name.")

        var superClass: Expression.Variable? = null
        if (match(TokenType.LESSER_THAN)) {
            consume(TokenType.IDENTIFIER, "Expected superclass name.")
            superClass = Expression.Variable(previous())
        }

        consume(TokenType.LEFT_BRACE, "Expected '{' before class body.")

        val methods: ArrayList<Statement.Function> = ArrayList()
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) methods.add(function("method"))

        consume(TokenType.RIGHT_BRACE, "Expected '}' after class body.")

        return Statement.Class(name, superClass, methods)
    }

    private fun varDeclaration(): Statement {
        val name = consume(TokenType.IDENTIFIER, "Expected a variable name.")
        var initializer: Expression? = null

        if (match(TokenType.EQUAL)) initializer = expression()
        consume(TokenType.SEMICOLON, "Expected ';' after variable declaration.")

        return Statement.Var(name, initializer)
    }

    private fun peek(): Token {
        return tokens[current]
    }

    private fun previous(): Token {
        return tokens[current - 1]
    }

    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        throw error(peek(), message)
    }

    private fun check(type: TokenType): Boolean {
        if (isAtEnd()) return false
        return peek().type == type
    }

    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }

        return false
    }

    private fun isAtEnd(): Boolean {
        return peek().type == TokenType.EOF
    }

    private fun synchronize() {
        advance()

        while (!isAtEnd()) {
            if (previous().type == TokenType.SEMICOLON) return

            when (peek().type) {
                TokenType.CLASS, TokenType.FUN, TokenType.VAR, TokenType.FOR, TokenType.IF, TokenType.WHILE, TokenType.PRINT, TokenType.RETURN -> return
                else -> advance()
            }
        }
    }

    private fun error(token: Token, message: String): ParseError {
        generateError(token, message)
        return ParseError()
    }
}