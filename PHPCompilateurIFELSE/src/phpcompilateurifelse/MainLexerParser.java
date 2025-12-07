package PHPCompilateurIFELSE;
import java.util.*;

public class MainLexerParser {
    
    public static void main(String[] args) {
        // Supprimer toutes les méthodes main() des classes LexerPHP et ParserPHP avant d'utiliser ce programme
        Scanner scanner = new Scanner(System.in);
        
        // Affichage de l'en-tête avec bordures
        System.out.println("╔═══════════════════════════════════════════════════════╗");
        System.out.println("║   ANALYSEUR LEXICAL ET SYNTAXIQUE PHP - IF/ELSE      ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝");
        System.out.println("Lancement du programme...\n");
        
        while (true) {
            System.out.println("╔═══════════════════════════════════════════════════════╗");
            System.out.println("║   ANALYSEUR LEXICAL ET SYNTAXIQUE PHP - IF/ELSE      ║");
            System.out.println("╚═══════════════════════════════════════════════════════╝");
            System.out.println("=".repeat(60));
            System.out.println("Entrez votre code PHP");
            System.out.println("- Terminez avec '###' OU simplement avec '?>'");
            System.out.println("- Tapez 'exit' pour quitter");
            System.out.println("=".repeat(60));
            System.out.println();
            
            // Lecture du code multi-lignes
            StringBuilder codeBuilder = new StringBuilder();
            String line;
            
            boolean codeEnded = false;
            while (scanner.hasNextLine()) {
                line = scanner.nextLine();
                
                if (line.trim().equals("exit")) {
                    System.out.println("\n👋 Au revoir !");
                    scanner.close();
                    return;
                }
                
                if (line.trim().equals("###")) {
                    break;
                }
                
                codeBuilder.append(line).append("\n");
                
                // Détection automatique de la fin du code PHP
                if (line.trim().equals("?>")) {
                    codeEnded = true;
                    break;
                }
            }
            
            String code = codeBuilder.toString().trim();
            
            if (code.isEmpty()) {
                System.out.println("⚠️  Aucun code saisi. Veuillez entrer du code PHP.");
                continue;
            }
            
            // Ajouter <?php si absent
            if (!code.startsWith("<?php") && !code.startsWith("<?")) {
                code = "<?php\n" + code;
            }
            
            try {
                System.out.println("\n" + "=".repeat(60));
                System.out.println("📊 PHASE 1 : ANALYSE LEXICALE");
                System.out.println("=".repeat(60));
                
                // Analyse lexicale
                List<LexerPHP.Token> tokens = LexerPHP.tokenize(code);
                
                System.out.println("✅ Tokens générés : " + tokens.size());
                System.out.println("\n--- LISTE DES TOKENS ---");
                for (LexerPHP.Token token : tokens) {
                    System.out.println(token);
                }
                
                // Statistiques
                System.out.println("\n--- STATISTIQUES ---");
                Map<String, Integer> stats = new HashMap<>();
                for (LexerPHP.Token t : tokens) {
                    stats.put(t.type, stats.getOrDefault(t.type, 0) + 1);
                }
                stats.forEach((type, count) -> 
                    System.out.println(type + " : " + count)
                );
                
                System.out.println("\n" + "=".repeat(60));
                System.out.println("🔍 PHASE 2 : ANALYSE SYNTAXIQUE");
                System.out.println("=".repeat(60));
                
                // Analyse syntaxique
                ParserPHP parser = new ParserPHP(tokens);
                boolean success = parser.parse();
                
                System.out.println("\n" + "=".repeat(60));
                if (success) {
                    System.out.println("✅ VALIDATION COMPLÈTE RÉUSSIE !");
                    System.out.println("Le code est lexicalement et syntaxiquement correct.");
                } else {
                    System.out.println("❌ VALIDATION ÉCHOUÉE");
                    System.out.println("Des erreurs syntaxiques ont été détectées.");
                }
                System.out.println("=".repeat(60));
                
            } catch (Exception e) {
                System.out.println("\n❌ ERREUR CRITIQUE : " + e.getMessage());
                System.out.println("Le programme a rencontré une erreur inattendue.");
                e.printStackTrace();
            }
            
            System.out.println("\n" + "─".repeat(60));
            System.out.println("Prêt pour une nouvelle analyse...\n");
        }
    }
}
