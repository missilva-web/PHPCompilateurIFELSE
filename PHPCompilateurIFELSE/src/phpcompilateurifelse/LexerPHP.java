package PHPCompilateurIFELSE;
import java.util.*;

public class LexerPHP {
    // ===========================
    // Mots-clés PHP et personnalisés
    // ===========================
    private static final Set<String> MOTS_CLES = new HashSet<>(Arrays.asList(
            // Mots-clés PHP standards
            "abstract", "and", "array", "as", "break", "callable", "case", "catch", "class",
            "clone", "const", "continue", "declare", "default", "die", "do", "echo", "else",
            "elseif", "empty", "enddeclare", "endfor", "endforeach", "endif", "endswitch",
            "endwhile", "eval", "exit", "extends", "final", "finally", "fn", "for", "foreach",
            "function", "global", "goto", "if", "implements", "include", "include_once",
            "instanceof", "insteadof", "interface", "isset", "list", "match", "namespace",
            "new", "or", "print", "private", "protected", "public", "readonly", "require",
            "require_once", "return", "static", "switch", "throw", "trait", "try", "unset",
            "use", "var", "while", "xor", "yield",
            // Types de données
            "int", "float", "bool", "string", "true", "false", "null",
            "object", "resource", "mixed", "void", "never",
            // Mots-clés magiques
            "__halt_compiler", "__CLASS__", "__DIR__", "__FILE__", "__FUNCTION__",
            "__LINE__", "__METHOD__", "__NAMESPACE__", "__TRAIT__",
            // Mots-clés personnalisés (REMPLACEZ PAR VOTRE NOM ET PRÉNOM)
            "MISSILVA", "BOUMOULA"
    ));

    // ===========================
    // DFA pour identificateurs et variables
    // Colonnes : 0 = lettre/_ , 1 = chiffre, 2 = $, 3 = autre
    // ===========================
    private static final int[][] MIdent = {
            {1, -1, 2, -1}, // S0 : lettre/_ -> S1, $ -> S2
            {1, 1, -1, -1}, // S1 : lettre/chiffre -> S1 (identificateur)
            {3, -1, -1, -1}, // S2 : $ suivi de lettre/_ -> S3
            {3, 3, -1, -1}  // S3 : variable PHP (lettre/chiffre -> S3)
    };
    private static final int EfIdent = 1;
    private static final int EfVar = 3;

    public static int colIdent(char c) {
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_') return 0;
        if (c >= '0' && c <= '9') return 1;
        if (c == '$') return 2;
        return 3;
    }

    // ===========================
    // DFA pour nombres
    // Colonnes : 0 = chiffre, 1 = point, 2 = e/E, 3 = +/-, 4 = autre
    // ===========================
    private static final int[][] MNumber = {
            {1, -1, -1, -1, -1}, // S0 : chiffre -> S1
            {1, 2, 4, -1, -1},   // S1 : chiffre -> S1, point -> S2, e/E -> S4
            {3, -1, 4, -1, -1},  // S2 : chiffre -> S3, e/E -> S4
            {3, -1, 4, -1, -1},  // S3 : chiffre -> S3, e/E -> S4 (float)
            {6, -1, -1, 5, -1},  // S4 : chiffre -> S6, +/- -> S5
            {6, -1, -1, -1, -1}, // S5 : chiffre -> S6
            {6, -1, -1, -1, -1}  // S6 : chiffre -> S6 (notation scientifique)
    };
    private static final int EfInt = 1;
    private static final int EfFloat = 3;
    private static final int EfScientific = 6;

    public static int colNumber(char c) {
        if (c >= '0' && c <= '9') return 0;
        if (c == '.') return 1;
        if (c == 'e' || c == 'E') return 2;
        if (c == '+' || c == '-') return 3;
        return 4;
    }

    // ===========================
    // DFA pour opérateurs
    // Colonnes : + - * / % = ! < > & | . : ? ^ ~ @ ; , ( ) { } [ ]
    // ===========================
    private static final int[][] MOp = {
            {1, 1, 2, 2, 2, 3, 3, 3, 3, 3, 3, 8, 9, 9, 2, 2, 10, 7, 7, 7, 7, 7, 7, 7, 7, -1}, // S0
            {4, 4, -1, -1, -1, 5, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, // S1 (+,-)
            {-1, -1, 5, -1, -1, 5, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, // S2 (*,/,%)
            {-1, -1, -1, -1, -1, 6, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, // S3 (=,!,<,>,&,|)
            {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, // S4 (++,--)
            {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, // S5 (*=,/=,%=,+=,-=)
            {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, // S6 (==,!=,<=,>=,&&,||)
            {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, // S7 délimiteurs
            {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 8, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, // S8 (.,...)
            {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 9, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, // S9 (:,::)
            {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}  // S10 (@)
    };

    private static final int EfOpIncDec = 4;
    private static final int EfOpAssign = 5;
    private static final int EfOpComp = 6;
    private static final int EfDelim = 7;
    private static final int EfOpConcat = 8;
    private static final int EfOpScope = 9;
    private static final int EfOpError = 10;

    public static int colOp(char c) {
        switch (c) {
            case '+': return 0;
            case '-': return 1;
            case '*': return 2;
            case '/': return 3;
            case '%': return 4;
            case '=': return 5;
            case '!': return 6;
            case '<': return 7;
            case '>': return 8;
            case '&': return 9;
            case '|': return 10;
            case '.': return 11;
            case ':': return 12;
            case '?': return 13;
            case '^': return 14;
            case '~': return 15;
            case '@': return 16;
            case ';': return 17;
            case ',': return 18;
            case '(': return 19;
            case ')': return 20;
            case '{': return 21;
            case '}': return 22;
            case '[': return 23;
            case ']': return 24;
            default: return 25;
        }
    }

    // ===========================
    // Classe Token
    // ===========================
    public static class Token {
        public final String type;
        public final String value;
        public final int line;

        public Token(String type, String value, int line) {
            this.type = type;
            this.value = value;
            this.line = line;
        }

        @Override
        public String toString() {
            return "[" + value + "] => type: " + type + ", ligne: " + line;
        }
    }

    // ===========================
    // Fonction principale du Lexer
    // ===========================
    public static List<Token> tokenize(String code) {
        List<Token> tokens = new ArrayList<>();
        code += "##"; // marqueur de fin
        int pos = 0, line = 1;

        // Détecter et ignorer le tag d'ouverture PHP
        if (code.startsWith("<?php")) {
            pos = 5;
            tokens.add(new Token("TAG_PHP", "<?php", line));
        } else if (code.startsWith("<?")) {
            pos = 2;
            tokens.add(new Token("TAG_PHP", "<?", line));
        }

        while (code.charAt(pos) != '#') {
            char c = code.charAt(pos);

            // Nouvelle ligne
            if (c == '\n') {
                line++;
                pos++;
                continue;
            }

            // Espaces (IGNORÉS)
            if (Character.isWhitespace(c)) {
                pos++;
                continue;
            }

            // ===========================
            // Tag de fermeture PHP
            // ===========================
            if (c == '?' && code.charAt(pos + 1) == '>') {
                tokens.add(new Token("TAG_PHP", "?>", line));
                pos += 2;
                continue;
            }

            // ===========================
            // Commentaires (IGNORÉS)
            // ===========================
            // Commentaire // 
            if (c == '/' && code.charAt(pos + 1) == '/') {
                pos += 2;
                while (code.charAt(pos) != '\n' && code.charAt(pos) != '#') {
                    pos++;
                }
                continue;
            }
            
            // Commentaire #
            if (c == '#' && code.charAt(pos + 1) != '#') {
                pos++;
                while (code.charAt(pos) != '\n' && code.charAt(pos) != '#') {
                    pos++;
                }
                continue;
            }
            
            // Commentaire /* */
            if (c == '/' && code.charAt(pos + 1) == '*') {
                pos += 2;
                while (code.charAt(pos) != '#') {
                    if (code.charAt(pos) == '*' && code.charAt(pos + 1) == '/') {
                        pos += 2;
                        break;
                    }
                    if (code.charAt(pos) == '\n') line++;
                    pos++;
                }
                continue;
            }

            // ===========================
            // Chaînes avec guillemets simples
            // ===========================
            if (c == '\'') {
                int start = pos;
                pos++;
                while (code.charAt(pos) != '\'' && code.charAt(pos) != '#') {
                    if (code.charAt(pos) == '\\') {
                        pos++;
                    }
                    if (code.charAt(pos) == '\n') line++;
                    pos++;
                }
                if (code.charAt(pos) == '\'') pos++;
                tokens.add(new Token("STRING", code.substring(start, pos), line));
                continue;
            }

            // ===========================
            // Chaînes avec guillemets doubles
            // ===========================
            if (c == '"') {
                int start = pos;
                pos++;
                while (code.charAt(pos) != '"' && code.charAt(pos) != '#') {
                    if (code.charAt(pos) == '\\') {
                        pos++;
                    }
                    if (code.charAt(pos) == '\n') line++;
                    pos++;
                }
                if (code.charAt(pos) == '"') pos++;
                tokens.add(new Token("STRING", code.substring(start, pos), line));
                continue;
            }

            // ===========================
            // Heredoc
            // ===========================
            if (c == '<' && code.charAt(pos + 1) == '<' && code.charAt(pos + 2) == '<') {
                int start = pos;
                pos += 3;
                tokens.add(new Token("HEREDOC", code.substring(start, pos), line));
                continue;
            }

            // ===========================
            // Variables et identificateurs
            // ===========================
            if (c == '$' || Character.isLetter(c) || c == '_') {
                int E = 0, start = pos;
                while (code.charAt(pos) != '#' && E != -1) {
                    int col = colIdent(code.charAt(pos));
                    int nextE = MIdent[E][col];
                    if (nextE == -1) break;
                    E = nextE;
                    pos++;
                }
                String mot = code.substring(start, pos);
                String type;
                if (E == EfVar) {
                    type = "VARIABLE";
                } else if (MOTS_CLES.contains(mot.toLowerCase())) {
                    type = "MOT_CLE";
                } else {
                    type = "IDENT";
                }
                tokens.add(new Token(type, mot, line));
                continue;
            }

            // ===========================
            // Nombres
            // ===========================
            if (Character.isDigit(c)) {
                int E = 0, start = pos;
                while (code.charAt(pos) != '#' && E != -1) {
                    int col = colNumber(code.charAt(pos));
                    int nextE = MNumber[E][col];
                    if (nextE == -1) break;
                    E = nextE;
                    pos++;
                }
                String mot = code.substring(start, pos);
                String type;
                if (E == EfScientific) type = "NOMBRE (SCIENTIFIC)";
                else if (E == EfFloat) type = "NOMBRE (FLOAT)";
                else type = "NOMBRE (INT)";
                tokens.add(new Token(type, mot, line));
                continue;
            }

            // ===========================
            // Opérateurs et délimiteurs
            // ===========================
            if ("+-*/%=!<>&|.:?^~@;,(){}[]".indexOf(c) != -1) {
                int E = 0, start = pos;
                while (code.charAt(pos) != '#' && E != -1) {
                    int col = colOp(code.charAt(pos));
                    int nextE = MOp[E][col];
                    if (nextE == -1) break;
                    E = nextE;
                    pos++;
                }
                String mot = code.substring(start, pos);
                String type;
                if (E == EfOpIncDec) type = "INC/DEC";
                else if (E == EfOpAssign) type = "OPERATEUR_ASSIGN";
                else if (E == EfOpComp) type = "COMPARATEUR";
                else if (E == EfDelim) type = "DELIMITEUR";
                else if (E == EfOpConcat) type = "CONCAT";
                else if (E == EfOpScope) type = "SCOPE";
                else if (E == EfOpError) type = "ERROR_SUPPRESSION";
                else type = "OPERATEUR";
                tokens.add(new Token(type, mot, line));
                continue;
            }

            // Caractère inconnu
            tokens.add(new Token("INCONNU", String.valueOf(c), line));
            pos++;
        }

        return tokens;
    }
}