import java.util.*;
import java.util.regex.*;

public class LexicalAnalyzerUtil {
    public static class Token {
        private final TokenType tkType;
        private final String value;
        private final int line;
        private final int column;

        public Token(TokenType tkType, String value, int line, int column) {
            this.tkType = tkType;
            this.value = value;
            this.line = line;
            this.column = column;
        }

        public TokenType getTokenType() { return tkType; }
        public String getValue() { return value; }
        public int getLine() { return line; }
        public int getColumn() { return column; }

        @Override
        public String toString() {
            return String.format("<%s, '%s'> @(%d,%d)", tkType, value, line, column);
        }
    }
public enum TokenType {
            // --- Keywords ---
            IF(null), ELSE(null), WHILE(null), FOR(null), DO(null), BREAK(null),

            // --- Data types / literals ---
            INTEGER("digit"), 
            FLOAT("digit"), 
            STRING(null), 
            CHAR(null),

            // --- Operators ---
            PLUS("+"), 
            MINUS("-"), 
            MULTIPLY("*"), 
            DIVIDE("/"),
            ASSIGN("="), 
            EQUALS("=="), 
            GREATER(">"), 
            LESS("<"), 
            GE(">="), 
            LE("<="), 
            NOT_EQ("!="),

            // --- Separators ---
            LPAREN("("), 
            RPAREN(")"), 
            LBRACE("{"), 
            RBRACE("}"), 
            SEMICOLON(";"), 
            COMMA(","),

            // --- Identifier ---
            // Changed from 'id' back to 'IDENTIFIER', but it still maps to grammar symbol "id"
            IDENTIFIER("id"),

            // --- Special ---
            EOF("$"), 
            UNKNOWN(null);

            // Field to hold the grammar symbol
            public final String grammarSymbol;

            TokenType(String grammarSymbol) {
                this.grammarSymbol = grammarSymbol;
            }
        }
    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();
    static {
        KEYWORDS.put("if", TokenType.IF);
        KEYWORDS.put("else", TokenType.ELSE);
        KEYWORDS.put("while", TokenType.WHILE);
        KEYWORDS.put("for", TokenType.FOR);
        KEYWORDS.put("do", TokenType.DO);
        KEYWORDS.put("break", TokenType.BREAK);
    }
    private static final Map<TokenType, String> TOKEN_REGEX = new LinkedHashMap<>();
    static {
        TOKEN_REGEX.put(TokenType.FLOAT, "\\d+\\.\\d+");
        TOKEN_REGEX.put(TokenType.INTEGER, "\\d+");
        TOKEN_REGEX.put(TokenType.STRING, "\"([^\"\\\\]|\\\\.)*\"");
        TOKEN_REGEX.put(TokenType.CHAR, "'[^']'");
        TOKEN_REGEX.put(TokenType.EQUALS, "==");
        TOKEN_REGEX.put(TokenType.NOT_EQ, "!=");
        TOKEN_REGEX.put(TokenType.GE, ">=");
        TOKEN_REGEX.put(TokenType.LE, "<=");
        TOKEN_REGEX.put(TokenType.ASSIGN, "=");
        TOKEN_REGEX.put(TokenType.GREATER, ">");
        TOKEN_REGEX.put(TokenType.LESS, "<");
        TOKEN_REGEX.put(TokenType.PLUS, "\\+");
        TOKEN_REGEX.put(TokenType.MINUS, "-");
        TOKEN_REGEX.put(TokenType.MULTIPLY, "\\*");
        TOKEN_REGEX.put(TokenType.DIVIDE, "/");
        TOKEN_REGEX.put(TokenType.LPAREN, "\\(");
        TOKEN_REGEX.put(TokenType.RPAREN, "\\)");
        TOKEN_REGEX.put(TokenType.LBRACE, "\\{");
        TOKEN_REGEX.put(TokenType.RBRACE, "\\}");
        TOKEN_REGEX.put(TokenType.SEMICOLON, ";");
        TOKEN_REGEX.put(TokenType.COMMA, ",");
        TOKEN_REGEX.put(TokenType.IDENTIFIER, "[a-zA-Z_][a-zA-Z0-9_]*");
    }

    private final String input;
    private int pos = 0;
    private int line = 1;
    private int col = 1;

    public LexicalAnalyzerUtil(String input) {
        this.input = input;
    }
    private void skipWhitespace() {
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '\n') {
                line++;
                col = 1;
                pos++;
            } else if (Character.isWhitespace(c)) {
                col++;
                pos++;
            } else {
                break;
            }
        }
    }
    public Token getNextToken() {
        skipWhitespace();
        if (pos >= input.length()) {
            return new Token(TokenType.EOF, "", line, col);
        }

        String remaining = input.substring(pos);

        for (Map.Entry<TokenType, String> entry : TOKEN_REGEX.entrySet()) {
            Pattern pattern = Pattern.compile("^(" + entry.getValue() + ")");
            Matcher matcher = pattern.matcher(remaining);
            if (matcher.find()) {
                String value = matcher.group();
                TokenType type = entry.getKey();

                // Check if identifier is actually a keyword
                if (type == TokenType.IDENTIFIER && KEYWORDS.containsKey(value)) {
                    type = KEYWORDS.get(value);
                }

                Token token = new Token(type, value, line, col);

                pos += value.length();
                col += value.length();
                return token;
            }
        }
        Token token = new Token(TokenType.UNKNOWN, String.valueOf(input.charAt(pos)), line, col);
        pos++;
        col++;
        return token;
    }
    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        Token token;
        while ((token = getNextToken()).getTokenType() != TokenType.EOF) {
            tokens.add(token);
        }
        return tokens;
    }
    
    public static void main() {
    Scanner sc = new Scanner(System.in);
    System.out.println("Enter your code (press ENTER then Ctrl+D / Ctrl+Z to finish):");
    String code = "";
    while (sc.hasNextLine()) {
        code += sc.nextLine() + "\n";  // append each line directly
    }
    sc.close();
    
    LexicalAnalyzerUtil lexer = new LexicalAnalyzerUtil(code);
    List<Token> tokens = lexer.tokenize();

    System.out.println("\n=== Tokens Found ===");
    for (Token t : tokens) {
        System.out.println(t);
    }
  }
}