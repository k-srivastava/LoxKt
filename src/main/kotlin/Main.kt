import lox.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

private val interpreter = Interpreter()

var hadError = false
var hadRuntimeError = false

fun main(args: Array<String>) {
    if (args.size > 1) {
        println("Usage: lox [script]")
        exitProcess(64)
    }
    else if (args.size == 1) runFile(args[0])
    else runPrompt()
}

fun runFile(path: String) {
    val bytes = Files.readAllBytes(Paths.get(path))
    run(String(bytes, Charset.defaultCharset()))

    if (hadError) exitProcess(65)
    if (hadRuntimeError) exitProcess(70)
}

fun runPrompt() {
    val input = InputStreamReader(System.`in`)
    val reader = BufferedReader(input)

    while (true) {
        print("> ")
        val line = reader.readLine() ?: break
        run(line)

        hadError = false
    }
}

fun run(source: String) {
    val scanner = Scanner(source)
    val tokens = scanner.scanTokens()

    val parser = Parser(tokens)
    val statements = parser.parse()

    if (hadError) return

    val resolver = Resolver(interpreter)
    resolver.resolve(statements)

    if (hadError) return

    interpreter.interpret(statements)
}


fun runtimeError(error: RuntimeError) {
    System.err.println("${error.message}\n[line ${error.token.line}]")
    hadRuntimeError = true
}

fun generateError(token: Token, message: String) {
    if (token.type == TokenType.EOF) reportError(token.line, " at end", message)
    else reportError(token.line, " at '${token.lexeme}'", message)
}

fun reportError(line: UInt, location: String = "", message: String) {
    System.err.println("[line $line] Error$location: $message")
    hadError = true
}
