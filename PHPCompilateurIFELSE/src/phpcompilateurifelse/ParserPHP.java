package PHPCompilateurIFELSE;
import java.util.*;

public class ParserPHP {
    private List<LexerPHP.Token> tokens;
    private int currentPos;
    private List<String> errors;
    private boolean hasErrors;

    public ParserPHP(List<LexerPHP.Token> tokens) {
        this.tokens = tokens;
        this.currentPos = 0;
        this.errors = new ArrayList<>();
        this.hasErrors = false;
    }

    // ===========================
    // Utilitaires
    // ===========================
    private LexerPHP.Token current() {
        if (currentPos >= tokens.size()) {
            return tokens.get(tokens.size() - 1);
        }
        return tokens.get(currentPos);
    }

    private LexerPHP.Token peek(int offset) {
        int pos = currentPos + offset;
        if (pos >= tokens.size()) {
            return tokens.get(tokens.size() - 1);
        }
        return tokens.get(pos);
    }

    private void advance() {
        if (currentPos < tokens.size() - 1) {
            currentPos++;
        }
    }

    private boolean match(String... types) {
        for (String type : types) {
            if (current().type.equals(type)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchValue(String value) {
        return current().value.equals(value);
    }

    private void addError(String message) {
        errors.add("Erreur ligne " + current().line + ": " + message);
        hasErrors = true;
    }

    private boolean expect(String type, String message) {
        if (!match(type)) {
            addError(message + " (trouve: " + current().value + ")");
            return false;
        }
        advance();
        return true;
    }

    private boolean expectValue(String value, String message) {
        if (!matchValue(value)) {
            addError(message + " (trouve: " + current().value + ")");
            return false;
        }
        advance();
        return true;
    }

    // ===========================
    // Analyse syntaxique principale
    // ===========================
    public boolean parse() {
        System.out.println("=== DEBUT DE L'ANALYSE SYNTAXIQUE ===\n");

        // Ignorer le tag PHP d'ouverture
        if (match("TAG_PHP") && current().value.startsWith("<?")) {
            advance();
        }

        while (currentPos < tokens.size()) {
            // Si on rencontre ?>, on sort proprement
            if (match("TAG_PHP") && matchValue("?>")) {
                advance();
                break;
            }
            
            int oldPos = currentPos;
            
            try {
                parseStatement();
            } catch (Exception e) {
                addError("Exception: " + e.getMessage());
                recoverFromError();
            }
            
            if (currentPos == oldPos) {
                addError("Blocage detecte, token ignore: " + current().value);
                advance();
            }
        }

        System.out.println("\n=== RESULTATS ===");
        if (hasErrors) {
            System.out.println("Analyse ECHOUEE : " + errors.size() + " erreur(s) detectee(s)\n");
            for (String error : errors) {
                System.out.println(error);
            }
        } else {
            System.out.println("Analyse REUSSIE : Programme syntaxiquement correct !");
        }

        return !hasErrors;
    }

    // ===========================
    // Récupération d'erreur
    // ===========================
    private void recoverFromError() {
        while (currentPos < tokens.size()) {
            if (match("DELIMITEUR") && (matchValue(";") || matchValue("}") || matchValue("{"))) {
                if (matchValue(";")) {
                    advance();
                }
                return;
            }
            if (match("MOT_CLE") && (matchValue("if") || matchValue("function") || 
                                     matchValue("class") || matchValue("while"))) {
                return;
            }
            advance();
        }
    }

    // ===========================
    // Instruction principale
    // ===========================
    private void parseStatement() {
        if (currentPos >= tokens.size()) {
            return;
        }

        // Ne pas traiter ?> ici car il peut être à l'intérieur d'un bloc
        if (match("TAG_PHP") && matchValue("?>")) {
            return; // Ne pas avancer, laisser parse() le gérer
        }

        if (match("MOT_CLE") && matchValue("class")) {
            parseClass();
        }
        else if (match("MOT_CLE") && matchValue("function")) {
            parseFunction();
        }
        else if (match("MOT_CLE") && matchValue("if")) {
            parseIf();
        }
        else if (match("MOT_CLE") && 
                (matchValue("while") || matchValue("for") || matchValue("foreach") || 
                 matchValue("switch") || matchValue("do"))) {
            System.out.println("Ignorer instruction: " + current().value + " (ligne " + current().line + ")");
            skipStatement();
        }
        else if (match("VARIABLE", "IDENT")) {
            parseAssignmentOrCall();
        }
        else if (match("MOT_CLE") && (matchValue("echo") || matchValue("print"))) {
            parseEcho();
        }
        else if (match("MOT_CLE") && matchValue("return")) {
            parseReturn();
        }
        else if (match("DELIMITEUR") && matchValue(";")) {
            advance();
        }
        else {
            addError("Instruction non reconnue: " + current().value);
            advance();
        }
    }

    // ===========================
    // Déclaration de classe - CONTINUE APRÈS ERREURS
    // ===========================
    private void parseClass() {
        System.out.println("Analyse: Declaration de classe (ligne " + current().line + ")");
        int classLine = current().line;
        advance();

        if (!expect("IDENT", "Nom de classe attendu")) {
            skipToDelimiter("}");
            return;
        }

        if (match("MOT_CLE") && matchValue("extends")) {
            advance();
            if (!expect("IDENT", "Nom de classe parente attendu")) {
                skipToDelimiter("}");
                return;
            }
        }

        if (match("MOT_CLE") && matchValue("implements")) {
            advance();
            while (true) {
                if (!expect("IDENT", "Nom d'interface attendu")) {
                    skipToDelimiter("}");
                    return;
                }
                
                if (match("DELIMITEUR") && matchValue(",")) {
                    advance();
                } else {
                    break;
                }
            }
        }

        if (!expectValue("{", "'{' attendu apres le nom de classe")) {
            skipToDelimiter("}");
            return;
        }

        while (!(match("DELIMITEUR") && matchValue("}"))) {
            // PROTECTION 1: Fin des tokens
            if (currentPos >= tokens.size()) {
                addError("'}' attendu pour fermer la classe (ligne " + classLine + ")");
                return; // Sortir de parseClass, parse() continue
            }
            
            // PROTECTION 2: Fin du code PHP
            if (match("TAG_PHP") && matchValue("?>")) {
                addError("'}' attendu avant '?>' - Classe (ligne " + classLine + ") non fermee");
                return; // Sortir de parseClass
            }
            
            int oldPos = currentPos;
            try {
                parseClassMember();
            } catch (Exception e) {
                addError("Erreur dans membre de classe");
                recoverFromError();
            }
            
            if (currentPos == oldPos) {
                advance();
            }
        }

        if (match("DELIMITEUR") && matchValue("}")) {
            advance();
        }
    }

    // ===========================
    // Membre de classe
    // ===========================
    private void parseClassMember() {
        if (match("MOT_CLE") && 
            (matchValue("public") || matchValue("private") || matchValue("protected"))) {
            advance();
        }

        if (match("MOT_CLE") && (matchValue("static") || matchValue("final"))) {
            advance();
        }

        if (match("MOT_CLE") && matchValue("function")) {
            parseFunction();
        } else if (match("VARIABLE")) {
            parsePropertyDeclaration();
        } else {
            addError("Membre de classe invalide");
            advance();
        }
    }

    // ===========================
    // Propriété de classe
    // ===========================
    private void parsePropertyDeclaration() {
        System.out.println("Analyse: Declaration de propriete (ligne " + current().line + ")");
        advance();

        if (match("OPERATEUR", "OPERATEUR_ASSIGN") && matchValue("=")) {
            advance();
            try {
                parseExpression();
            } catch (Exception e) {
                recoverFromError();
            }
        }

        expectValue(";", "';' attendu apres la declaration de propriete");
    }

    // ===========================
    // Déclaration de fonction - CONTINUE APRÈS ERREURS
    // ===========================
    private void parseFunction() {
        System.out.println("Analyse: Declaration de fonction (ligne " + current().line + ")");
        int funcLine = current().line;
        advance();

        if (!expect("IDENT", "Nom de fonction attendu")) {
            skipToDelimiter("}");
            return;
        }

        if (!expectValue("(", "'(' attendu apres le nom de fonction")) {
            skipToDelimiter("}");
            return;
        }

        if (!(match("DELIMITEUR") && matchValue(")"))) {
            while (true) {
                if (match("MOT_CLE") && (matchValue("int") || matchValue("string") || 
                                         matchValue("float") || matchValue("bool") || 
                                         matchValue("array") || matchValue("mixed"))) {
                    advance();
                }
                
                if (match("VARIABLE")) {
                    advance();
                    if (match("OPERATEUR", "OPERATEUR_ASSIGN") && matchValue("=")) {
                        advance();
                        try {
                            parseExpression();
                        } catch (Exception e) {
                            recoverFromError();
                        }
                    }
                } else {
                    addError("Parametre invalide");
                    break;
                }
                
                if (match("DELIMITEUR") && matchValue(",")) {
                    advance();
                } else {
                    break;
                }
            }
        }

        if (!expectValue(")", "')' attendu apres les parametres")) {
            skipToDelimiter("}");
            return;
        }

        if (match("SCOPE") && matchValue(":")) {
            advance();
            if (match("MOT_CLE")) {
                advance();
            }
        }

        if (!expectValue("{", "'{' attendu pour le corps de fonction")) {
            skipToDelimiter("}");
            return;
        }

        while (!(match("DELIMITEUR") && matchValue("}"))) {
            // PROTECTION 1: Fin des tokens
            if (currentPos >= tokens.size()) {
                addError("'}' attendu pour fermer la fonction (ligne " + funcLine + ")");
                return; // Sortir de parseFunction, parse() continue
            }
            
            // PROTECTION 2: Fin du code PHP
            if (match("TAG_PHP") && matchValue("?>")) {
                addError("'}' attendu avant '?>' - Fonction (ligne " + funcLine + ") non fermee");
                return; // Sortir de parseFunction
            }
            
            int oldPos = currentPos;
            try {
                parseStatement();
            } catch (Exception e) {
                addError("Erreur dans fonction");
                recoverFromError();
            }
            
            if (currentPos == oldPos) {
                advance();
            }
        }

        if (match("DELIMITEUR") && matchValue("}")) {
            advance();
        }
    }

    // ===========================
    // Instruction IF/ELSE (CORRIGÉE - CONTINUE APRÈS ERREURS)
    // ===========================
    private void parseIf() {
        System.out.println("Analyse: Instruction IF (ligne " + current().line + ")");
        int ifLine = current().line;
        advance();

        if (!expectValue("(", "'(' attendu apres if")) {
            recoverFromError();
        } else {
            // Vérifier si la condition est vide
            if (match("DELIMITEUR") && matchValue(")")) {
                addError("Condition vide dans if");
                advance();
            } else {
                // Condition complète (peut contenir &&, ||, etc.)
                try {
                    parseCondition();
                } catch (Exception e) {
                    recoverFromError();
                }

                if (!expectValue(")", "')' attendu apres la condition")) {
                    recoverFromError();
                }
            }
        }

        // Bloc THEN
        if (match("DELIMITEUR") && matchValue("{")) {
            advance();
            
            while (!(match("DELIMITEUR") && matchValue("}"))) {
                // PROTECTION 1: Fin des tokens
                if (currentPos >= tokens.size()) {
                    addError("'}' attendu pour fermer le bloc if (ligne " + ifLine + ")");
                    return; // Sortir de parseIf, mais parse() continue
                }
                
                // PROTECTION 2: Fin du code PHP - NE PAS RETOURNER, juste signaler l'erreur
                if (match("TAG_PHP") && matchValue("?>")) {
                    addError("'}' attendu avant '?>' - Bloc IF (ligne " + ifLine + ") non ferme");
                    return; // Sortir de parseIf
                }
                
                int oldPos = currentPos;
                try {
                    parseStatement();
                } catch (Exception e) {
                    recoverFromError();
                }
                
                if (currentPos == oldPos) {
                    addError("Token non consomme dans bloc if: " + current().value);
                    advance();
                }
            }
            
            if (match("DELIMITEUR") && matchValue("}")) {
                advance();
            }
        } else {
            try {
                parseStatement();
            } catch (Exception e) {
                recoverFromError();
            }
        }

        // ELSEIF/ELSE - CONTINUE MÊME APRÈS ERREURS
        while (match("MOT_CLE") && (matchValue("elseif") || matchValue("else"))) {
            if (matchValue("else")) {
                int elseLine = current().line;
                advance();
                
                if (match("MOT_CLE") && matchValue("if")) {
                    System.out.println("Analyse: Instruction ELSE IF (ligne " + current().line + ")");
                    int elseifLine = current().line;
                    advance();
                    
                    if (!expectValue("(", "'(' attendu apres else if")) {
                        recoverFromError();
                    } else {
                        try {
                            parseCondition();
                        } catch (Exception e) {
                            recoverFromError();
                        }
                        
                        if (!expectValue(")", "')' attendu apres la condition")) {
                            recoverFromError();
                        }
                    }

                    if (match("DELIMITEUR") && matchValue("{")) {
                        advance();
                        
                        while (!(match("DELIMITEUR") && matchValue("}"))) {
                            // PROTECTION 1: Fin des tokens
                            if (currentPos >= tokens.size()) {
                                addError("'}' attendu pour fermer le bloc else if (ligne " + elseifLine + ")");
                                return;
                            }
                            
                            // PROTECTION 2: Fin du code PHP
                            if (match("TAG_PHP") && matchValue("?>")) {
                                addError("'}' attendu avant '?>' - Bloc ELSE IF (ligne " + elseifLine + ") non ferme");
                                return;
                            }
                            
                            int oldPos = currentPos;
                            try {
                                parseStatement();
                            } catch (Exception e) {
                                recoverFromError();
                            }
                            
                            if (currentPos == oldPos) {
                                advance();
                            }
                        }
                        
                        if (match("DELIMITEUR") && matchValue("}")) {
                            advance();
                        }
                    } else {
                        try {
                            parseStatement();
                        } catch (Exception e) {
                            recoverFromError();
                        }
                    }
                } else {
                    System.out.println("Analyse: Instruction ELSE (ligne " + elseLine + ")");
                    
                    if (match("DELIMITEUR") && matchValue("{")) {
                        advance();
                        
                        while (!(match("DELIMITEUR") && matchValue("}"))) {
                            // PROTECTION 1: Fin des tokens
                            if (currentPos >= tokens.size()) {
                                addError("'}' attendu pour fermer le bloc else (ligne " + elseLine + ")");
                                return;
                            }
                            
                            // PROTECTION 2: Fin du code PHP
                            if (match("TAG_PHP") && matchValue("?>")) {
                                addError("'}' attendu avant '?>' - Bloc ELSE (ligne " + elseLine + ") non ferme");
                                return;
                            }
                            
                            int oldPos = currentPos;
                            try {
                                parseStatement();
                            } catch (Exception e) {
                                recoverFromError();
                            }
                            
                            if (currentPos == oldPos) {
                                advance();
                            }
                        }
                        
                        if (match("DELIMITEUR") && matchValue("}")) {
                            advance();
                        }
                    } else {
                        try {
                            parseStatement();
                        } catch (Exception e) {
                            recoverFromError();
                        }
                    }
                    break;
                }
            } else {
                System.out.println("Analyse: Instruction ELSEIF (ligne " + current().line + ")");
                int elseifLine = current().line;
                advance();

                if (!expectValue("(", "'(' attendu apres elseif")) {
                    recoverFromError();
                } else {
                    try {
                        parseCondition();
                    } catch (Exception e) {
                        recoverFromError();
                    }
                    
                    if (!expectValue(")", "')' attendu apres la condition")) {
                        recoverFromError();
                    }
                }

                if (match("DELIMITEUR") && matchValue("{")) {
                    advance();
                    
                    while (!(match("DELIMITEUR") && matchValue("}"))) {
                        // PROTECTION 1: Fin des tokens
                        if (currentPos >= tokens.size()) {
                            addError("'}' attendu pour fermer le bloc elseif (ligne " + elseifLine + ")");
                            return;
                        }
                        
                        // PROTECTION 2: Fin du code PHP
                        if (match("TAG_PHP") && matchValue("?>")) {
                            addError("'}' attendu avant '?>' - Bloc ELSEIF (ligne " + elseifLine + ") non ferme");
                            return;
                        }
                        
                        int oldPos = currentPos;
                        try {
                            parseStatement();
                        } catch (Exception e) {
                            recoverFromError();
                        }
                        
                        if (currentPos == oldPos) {
                            advance();
                        }
                    }
                    
                    if (match("DELIMITEUR") && matchValue("}")) {
                        advance();
                    }
                } else {
                    try {
                        parseStatement();
                    } catch (Exception e) {
                        recoverFromError();
                    }
                }
            }
        }
    }

    // ===========================
    // NOUVELLE MÉTHODE : Analyse de condition complète
    // Grammaire : condition → expression ((&& | ||) expression)*
    // ===========================
    private void parseCondition() {
        // Parse première expression
        parseExpression();
        
        // Parse les opérateurs logiques et expressions suivantes
        while (match("OPERATEUR_LOGIQUE") && (matchValue("&&") || matchValue("||"))) {
            advance(); // Consommer && ou ||
            parseExpression(); // Parse l'expression suivante
        }
    }

    // ===========================
    // Affectation ou appel
    // ===========================
    private void parseAssignmentOrCall() {
        System.out.println("Analyse: Affectation/Appel (ligne " + current().line + ")");
        advance();

        if (match("DELIMITEUR") && matchValue("(")) {
            advance();
            
            if (!(match("DELIMITEUR") && matchValue(")"))) {
                while (true) {
                    try {
                        parseExpression();
                    } catch (Exception e) {
                        recoverFromError();
                    }
                    
                    if (match("DELIMITEUR") && matchValue(",")) {
                        advance();
                    } else {
                        break;
                    }
                }
            }
            
            expectValue(")", "')' attendu apres les arguments");
            expectValue(";", "';' attendu apres l'appel de fonction");
        }
        else if (match("OPERATEUR") && (matchValue("->") || matchValue("::"))) {
            advance();
            
            if (!expect("IDENT", "Nom de propriete/methode attendu")) {
                return;
            }
            
            if (match("DELIMITEUR") && matchValue("(")) {
                advance();
                
                if (!(match("DELIMITEUR") && matchValue(")"))) {
                    while (true) {
                        try {
                            parseExpression();
                        } catch (Exception e) {
                            recoverFromError();
                        }
                        
                        if (match("DELIMITEUR") && matchValue(",")) {
                            advance();
                        } else {
                            break;
                        }
                    }
                }
                
                expectValue(")", "')' attendu");
            }
            
            expectValue(";", "';' attendu");
        }
        else if (match("OPERATEUR", "OPERATEUR_ASSIGN", "INC/DEC")) {
            advance();
            
            if (!(match("DELIMITEUR") && matchValue(";"))) {
                try {
                    parseExpression();
                } catch (Exception e) {
                    recoverFromError();
                }
            }
            
            expectValue(";", "';' attendu apres l'affectation");
        } else {
            expectValue(";", "';' attendu");
        }
    }

    // ===========================
    // Echo/Print
    // ===========================
    private void parseEcho() {
        System.out.println("Analyse: Echo/Print (ligne " + current().line + ")");
        advance();
        
        while (true) {
            try {
                parseExpression();
            } catch (Exception e) {
                recoverFromError();
            }
            
            if (match("DELIMITEUR") && matchValue(",")) {
                advance();
            } else {
                break;
            }
        }
        
        expectValue(";", "';' attendu apres echo");
    }

    // ===========================
    // Return
    // ===========================
    private void parseReturn() {
        System.out.println("Analyse: Return (ligne " + current().line + ")");
        advance();
        
        if (!(match("DELIMITEUR") && matchValue(";"))) {
            try {
                parseExpression();
            } catch (Exception e) {
                recoverFromError();
            }
        }
        
        expectValue(";", "';' attendu apres return");
    }

    // ===========================
    // Expression (Recursivite descendante)
    // ===========================
    private void parseExpression() {
        parseComparison();
    }

    private void parseComparison() {
        parseTerm();

        while (match("COMPARATEUR", "OPERATEUR") && 
               (matchValue("==") || matchValue("!=") || matchValue("<") || 
                matchValue(">") || matchValue("<=") || matchValue(">="))) {
            advance();
            parseTerm();
        }
    }

    private void parseTerm() {
        parseFactor();

        while (match("OPERATEUR", "CONCAT") && 
               (matchValue("+") || matchValue("-") || matchValue("."))) {
            advance();
            parseFactor();
        }
    }

    private void parseFactor() {
        parsePrimary();

        while (match("OPERATEUR") && 
               (matchValue("*") || matchValue("/") || matchValue("%"))) {
            advance();
            parsePrimary();
        }
    }

    private void parsePrimary() {
        if (match("DELIMITEUR") && matchValue("(")) {
            advance();
            parseExpression();
            expectValue(")", "')' attendu");
        }
        else if (match("COMPARATEUR", "OPERATEUR") && (matchValue("!") || matchValue("-"))) {
            advance();
            parsePrimary();
        }
        else if (match("VARIABLE", "NOMBRE (INT)", "NOMBRE (FLOAT)", 
                       "NOMBRE (SCIENTIFIC)", "STRING", "MOT_CLE")) {
            advance();
            
            if (match("DELIMITEUR") && matchValue("[")) {
                advance();
                parseExpression();
                expectValue("]", "']' attendu");
            }
        }
        else if (match("DELIMITEUR") && matchValue("[")) {
            advance();
            
            if (!(match("DELIMITEUR") && matchValue("]"))) {
                while (true) {
                    parseExpression();
                    
                    if (match("DELIMITEUR") && matchValue(",")) {
                        advance();
                    } else {
                        break;
                    }
                }
            }
            
            expectValue("]", "']' attendu");
        }
        else if (match("IDENT")) {
            advance();
            
            if (match("DELIMITEUR") && matchValue("(")) {
                advance();
                
                if (!(match("DELIMITEUR") && matchValue(")"))) {
                    while (true) {
                        parseExpression();
                        
                        if (match("DELIMITEUR") && matchValue(",")) {
                            advance();
                        } else {
                            break;
                        }
                    }
                }
                
                expectValue(")", "')' attendu");
            }
        } else {
            addError("Expression invalide: " + current().value);
            advance();
        }
    }

    // ===========================
    // Utilitaires de saut
    // ===========================
    private void skipStatement() {
        int braceCount = 0;
        while (currentPos < tokens.size()) {
            if (match("DELIMITEUR") && matchValue("{")) braceCount++;
            if (match("DELIMITEUR") && matchValue("}")) braceCount--;
            if (match("DELIMITEUR") && matchValue(";") && braceCount == 0) {
                advance();
                return;
            }
            advance();
            if (braceCount < 0) return;
        }
    }

    private void skipToDelimiter(String delimiter) {
        while (currentPos < tokens.size() && 
               !(match("DELIMITEUR") && matchValue(delimiter))) {
            advance();
        }
        if (match("DELIMITEUR") && matchValue(delimiter)) {
            advance();
        }
    }
}
