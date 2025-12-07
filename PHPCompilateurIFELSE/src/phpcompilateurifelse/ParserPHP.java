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

        while (currentPos < tokens.size() && !match("TAG_PHP")) {
            try {
                parseStatement();
            } catch (Exception e) {
                addError("Exception: " + e.getMessage());
                // Récupération : avancer jusqu'au prochain point-virgule ou accolade
                recoverFromError();
            }
        }

        // Affichage des résultats
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
        // Chercher le prochain point d'ancrage sûr
        while (currentPos < tokens.size()) {
            if (match("DELIMITEUR") && (matchValue(";") || matchValue("}") || matchValue("{"))) {
                advance();
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
        // Protection contre boucle infinie
        if (currentPos >= tokens.size()) {
            return;
        }

        // Ignorer les tags de fermeture PHP
        if (match("TAG_PHP") && matchValue("?>")) {
            advance();
            return;
        }

        // Déclaration de classe
        if (match("MOT_CLE") && matchValue("class")) {
            parseClass();
        }
        // Déclaration de fonction
        else if (match("MOT_CLE") && matchValue("function")) {
            parseFunction();
        }
        // Instruction if/else (PRINCIPALE)
        else if (match("MOT_CLE") && matchValue("if")) {
            parseIf();
        }
        // Instructions à ignorer (while, for, foreach, switch, etc.)
        else if (match("MOT_CLE") && 
                (matchValue("while") || matchValue("for") || matchValue("foreach") || 
                 matchValue("switch") || matchValue("do"))) {
            System.out.println("Ignorer instruction: " + current().value + " (ligne " + current().line + ")");
            skipStatement();
        }
        // Affectation ou appel de fonction
        else if (match("VARIABLE", "IDENT")) {
            parseAssignmentOrCall();
        }
        // Echo/Print
        else if (match("MOT_CLE") && (matchValue("echo") || matchValue("print"))) {
            parseEcho();
        }
        // Return
        else if (match("MOT_CLE") && matchValue("return")) {
            parseReturn();
        }
        // Délimiteur seul (point-virgule orphelin)
        else if (match("DELIMITEUR") && matchValue(";")) {
            advance();
        }
        // Autre
        else {
            addError("Instruction non reconnue: " + current().value);
            advance(); // Continuer malgré l'erreur
        }
    }

    // ===========================
    // Déclaration de classe
    // ===========================
    private void parseClass() {
        System.out.println("Analyse: Declaration de classe (ligne " + current().line + ")");
        advance(); // 'class'

        if (!expect("IDENT", "Nom de classe attendu")) {
            skipToDelimiter("}");
            return;
        }

        // Héritage optionnel
        if (match("MOT_CLE") && matchValue("extends")) {
            advance();
            if (!expect("IDENT", "Nom de classe parente attendu")) {
                skipToDelimiter("}");
                return;
            }
        }

        // Interfaces optionnelles
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

        // Corps de la classe
        while (!match("DELIMITEUR") || !matchValue("}")) {
            if (currentPos >= tokens.size()) {
                addError("'}' attendu pour fermer la classe");
                return;
            }
            try {
                parseClassMember();
            } catch (Exception e) {
                addError("Erreur dans membre de classe");
                recoverFromError();
            }
        }

        expectValue("}", "'}' attendu pour fermer la classe");
    }

    // ===========================
    // Membre de classe
    // ===========================
    private void parseClassMember() {
        // Visibilité
        if (match("MOT_CLE") && 
            (matchValue("public") || matchValue("private") || matchValue("protected"))) {
            advance();
        }

        // Static/Final
        if (match("MOT_CLE") && (matchValue("static") || matchValue("final"))) {
            advance();
        }

        // Fonction ou variable
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
        advance(); // variable

        // Initialisation optionnelle
        if (match("OPERATEUR", "OPERATEUR_ASSIGN") && matchValue("=")) {
            advance();
            parseExpression();
        }

        if (!expectValue(";", "';' attendu apres la declaration de propriete")) {
            // Récupération : chercher le prochain point-virgule
            recoverFromError();
        }
    }

    // ===========================
    // Déclaration de fonction
    // ===========================
    private void parseFunction() {
        System.out.println("Analyse: Declaration de fonction (ligne " + current().line + ")");
        advance(); // 'function'

        if (!expect("IDENT", "Nom de fonction attendu")) {
            skipToDelimiter("}");
            return;
        }

        if (!expectValue("(", "'(' attendu apres le nom de fonction")) {
            skipToDelimiter("}");
            return;
        }

        // Paramètres
        if (!match("DELIMITEUR") || !matchValue(")")) {
            while (true) {
                // Type optionnel (int, string, etc.)
                if (match("MOT_CLE") && (matchValue("int") || matchValue("string") || 
                                         matchValue("float") || matchValue("bool") || 
                                         matchValue("array") || matchValue("mixed"))) {
                    advance();
                }
                
                if (match("VARIABLE")) {
                    advance();
                    // Valeur par défaut optionnelle
                    if (match("OPERATEUR", "OPERATEUR_ASSIGN") && matchValue("=")) {
                        advance();
                        parseExpression();
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

        // Type de retour optionnel (: string, : int, etc.)
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

        // Corps de la fonction
        while (!match("DELIMITEUR") || !matchValue("}")) {
            if (currentPos >= tokens.size()) {
                addError("'}' attendu pour fermer la fonction");
                return;
            }
            try {
                parseStatement();
            } catch (Exception e) {
                addError("Erreur dans fonction");
                recoverFromError();
            }
        }

        expectValue("}", "'}' attendu pour fermer la fonction");
    }

    // ===========================
    // Instruction IF/ELSE (PRINCIPALE)
    // ===========================
    private void parseIf() {
        System.out.println("Analyse: Instruction IF (ligne " + current().line + ")");
        advance(); // 'if'

        if (!expectValue("(", "'(' attendu apres if")) {
            skipStatement();
            return;
        }

        // Condition
        parseExpression();

        if (!expectValue(")", "')' attendu apres la condition")) {
            skipStatement();
            return;
        }

        // Bloc THEN
        if (match("DELIMITEUR") && matchValue("{")) {
            advance();
            while (!match("DELIMITEUR") || !matchValue("}")) {
                if (currentPos >= tokens.size()) {
                    addError("'}' attendu pour fermer le bloc if");
                    return;
                }
                try {
                    parseStatement();
                } catch (Exception e) {
                    recoverFromError();
                }
            }
            expectValue("}", "'}' attendu pour fermer le bloc if");
        } else {
            // Instruction unique sans accolades
            parseStatement();
        }

        // ELSEIF (gère aussi "else if" en deux mots)
        while (match("MOT_CLE") && (matchValue("elseif") || matchValue("else"))) {
            if (matchValue("else")) {
                advance();
                // Vérifier si c'est "else if" (deux mots)
                if (match("MOT_CLE") && matchValue("if")) {
                    System.out.println("Analyse: Instruction ELSE IF (ligne " + current().line + ")");
                    advance(); // 'if'
                    
                    if (!expectValue("(", "'(' attendu apres else if")) {
                        skipStatement();
                        continue;
                    }

                    parseExpression();

                    if (!expectValue(")", "')' attendu apres la condition")) {
                        skipStatement();
                        continue;
                    }

                    if (match("DELIMITEUR") && matchValue("{")) {
                        advance();
                        while (!match("DELIMITEUR") || !matchValue("}")) {
                            if (currentPos >= tokens.size()) {
                                addError("'}' attendu pour fermer le bloc else if");
                                return;
                            }
                            try {
                                parseStatement();
                            } catch (Exception e) {
                                recoverFromError();
                            }
                        }
                        expectValue("}", "'}' attendu pour fermer le bloc else if");
                    } else {
                        parseStatement();
                    }
                } else {
                    // C'est un vrai ELSE
                    System.out.println("Analyse: Instruction ELSE (ligne " + current().line + ")");
                    
                    if (match("DELIMITEUR") && matchValue("{")) {
                        advance();
                        while (!match("DELIMITEUR") || !matchValue("}")) {
                            if (currentPos >= tokens.size()) {
                                addError("'}' attendu pour fermer le bloc else");
                                return;
                            }
                            try {
                                parseStatement();
                            } catch (Exception e) {
                                recoverFromError();
                            }
                        }
                        expectValue("}", "'}' attendu pour fermer le bloc else");
                    } else {
                        parseStatement();
                    }
                    break; // Sortir après un else
                }
            } else {
                // ELSEIF en un seul mot
                System.out.println("Analyse: Instruction ELSEIF (ligne " + current().line + ")");
                advance();

                if (!expectValue("(", "'(' attendu apres elseif")) {
                    skipStatement();
                    continue;
                }

                parseExpression();

                if (!expectValue(")", "')' attendu apres la condition")) {
                    skipStatement();
                    continue;
                }

                if (match("DELIMITEUR") && matchValue("{")) {
                    advance();
                    while (!match("DELIMITEUR") || !matchValue("}")) {
                        if (currentPos >= tokens.size()) {
                            addError("'}' attendu pour fermer le bloc elseif");
                            return;
                        }
                        try {
                            parseStatement();
                        } catch (Exception e) {
                            recoverFromError();
                        }
                    }
                    expectValue("}", "'}' attendu pour fermer le bloc elseif");
                } else {
                    parseStatement();
                }
            }
        }
    }

    // ===========================
    // Affectation ou appel
    // ===========================
    private void parseAssignmentOrCall() {
        System.out.println("Analyse: Affectation/Appel (ligne " + current().line + ")");
        advance(); // variable ou ident

        // Appel de fonction
        if (match("DELIMITEUR") && matchValue("(")) {
            advance();
            // Arguments
            if (!match("DELIMITEUR") || !matchValue(")")) {
                while (true) {
                    parseExpression();
                    
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
        // Accès propriété/méthode
        else if (match("OPERATEUR") && (matchValue("->") || matchValue("::"))) {
            advance();
            if (!expect("IDENT", "Nom de propriete/methode attendu")) return;
            
            if (match("DELIMITEUR") && matchValue("(")) {
                advance();
                if (!match("DELIMITEUR") || !matchValue(")")) {
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
            expectValue(";", "';' attendu");
        }
        // Affectation
        else if (match("OPERATEUR", "OPERATEUR_ASSIGN", "INC/DEC")) {
            advance();
            if (!match("DELIMITEUR") || !matchValue(";")) {
                parseExpression();
            }
            if (!expectValue(";", "';' attendu apres l'affectation")) {
                recoverFromError();
            }
        } else {
            if (!expectValue(";", "';' attendu")) {
                recoverFromError();
            }
        }
    }

    // ===========================
    // Echo/Print
    // ===========================
    private void parseEcho() {
        System.out.println("Analyse: Echo/Print (ligne " + current().line + ")");
        advance();
        
        while (true) {
            parseExpression();
            
            if (match("DELIMITEUR") && matchValue(",")) {
                advance();
            } else {
                break;
            }
        }
        
        if (!expectValue(";", "';' attendu apres echo")) {
            recoverFromError();
        }
    }

    // ===========================
    // Return
    // ===========================
    private void parseReturn() {
        System.out.println("Analyse: Return (ligne " + current().line + ")");
        advance();
        
        if (!match("DELIMITEUR") || !matchValue(";")) {
            parseExpression();
        }
        
        if (!expectValue(";", "';' attendu apres return")) {
            recoverFromError();
        }
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
                matchValue(">") || matchValue("<=") || matchValue(">=") ||
                matchValue("&&") || matchValue("||"))) {
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
        // Parenthèses
        if (match("DELIMITEUR") && matchValue("(")) {
            advance();
            parseExpression();
            expectValue(")", "')' attendu");
        }
        // Négation
        else if (match("COMPARATEUR", "OPERATEUR") && (matchValue("!") || matchValue("-"))) {
            advance();
            parsePrimary();
        }
        // Variable, nombre, chaîne, booléen
        else if (match("VARIABLE", "NOMBRE (INT)", "NOMBRE (FLOAT)", 
                       "NOMBRE (SCIENTIFIC)", "STRING", "MOT_CLE")) {
            advance();
            
            // Accès tableau
            if (match("DELIMITEUR") && matchValue("[")) {
                advance();
                parseExpression();
                expectValue("]", "']' attendu");
            }
        }
        // Tableau
        else if (match("DELIMITEUR") && matchValue("[")) {
            advance();
            if (!match("DELIMITEUR") || !matchValue("]")) {
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
        // Identifiant (fonction)
        else if (match("IDENT")) {
            advance();
            if (match("DELIMITEUR") && matchValue("(")) {
                advance();
                if (!match("DELIMITEUR") || !matchValue(")")) {
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
            advance(); // Continuer malgré l'erreur
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
               (!match("DELIMITEUR") || !matchValue(delimiter))) {
            advance();
        }
        if (match("DELIMITEUR") && matchValue(delimiter)) {
            advance();
        }
    }
}