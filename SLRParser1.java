import java.util.*;

public class SLRParser1 {
    static class Production {
        String lhs;
        List<String> rhs;

        public Production(String lhs, List<String> rhs) {
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        public String toString() {
            return lhs + " -> " + String.join(" ", rhs);
        }
    }

    static class Item {
        Production production;
        int dotPosition;

        public Item(Production production, int dotPosition) {
            this.production = production;
            this.dotPosition = dotPosition;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Item item = (Item) o;
            return dotPosition == item.dotPosition && Objects.equals(production, item.production);
        }

        @Override
        public int hashCode() {
            return Objects.hash(production, dotPosition);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(production.lhs).append(" -> ");
            for (int i = 0; i < production.rhs.size(); i++) {
                if (i == dotPosition) sb.append(". ");
                sb.append(production.rhs.get(i)).append(" ");
            }
            if (dotPosition == production.rhs.size()) sb.append(".");
            return sb.toString().trim();
        }
    }

    // --- Parser State ---
    private List<Production> grammar = new ArrayList<>();
    private Set<String> nonTerminals = new HashSet<>();
    private Set<String> terminals = new HashSet<>();
    private Map<String, Set<String>> firstSets = new HashMap<>();
    private Map<String, Set<String>> followSets = new HashMap<>();
    private List<Set<Item>> canonicalCollection = new ArrayList<>();
    private Map<Integer, Map<String, String>> actionTable = new HashMap<>();
    private Map<Integer, Map<String, Integer>> gotoTable = new HashMap<>();
    private String startSymbol;

    public SLRParser1() {
        setupGrammar();
        computeFirstSets();
        computeFollowSets();
        buildCanonicalCollection();
        buildParsingTable();
    }

    // 1. Define a Sample Grammar (Expression Grammar)
    // E -> E + T | T
    // T -> T * F | F
    // F -> ( E ) | id
    private void setupGrammar() {
        // Augmented Grammar: S' -> E
        addProduction("S'", Arrays.asList("E")); 
        addProduction("E", Arrays.asList("E", "+", "T"));
        addProduction("E", Arrays.asList("T"));
        addProduction("T", Arrays.asList("T", "*", "F"));
        addProduction("T", Arrays.asList("F"));
        addProduction("F", Arrays.asList("(", "E", ")"));
        addProduction("F", Arrays.asList("id"));
        addProduction("F", Arrays.asList("digit")); 

        startSymbol = "S'";
        
        System.out.println("=== Grammar Rules ===");
        for(int i=0; i<grammar.size(); i++) {
            System.out.println(i + ": " + grammar.get(i));
        }
        System.out.println("=====================");
    }

    private void addProduction(String lhs, List<String> rhs) {
        grammar.add(new Production(lhs, rhs));
        nonTerminals.add(lhs);
        for (String sym : rhs) {
            if (!isNonTerminal(sym)) terminals.add(sym);
        }
    }
    
    // Helper to distinguish terminals vs Non-Terminals
   
    private boolean isNonTerminal(String s) {
        
        return nonTerminals.contains(s);
    }

    // --- 2. First and Follow Sets ---

    private void computeFirstSets() {
        // Initialize
        for (String t : terminals) firstSets.put(t, new HashSet<>(Arrays.asList(t)));
        for (String nt : nonTerminals) firstSets.put(nt, new HashSet<>());

        boolean changed = true;
        while (changed) {
            changed = false;
            for (Production p : grammar) {
                Set<String> target = firstSets.get(p.lhs);
                int originalSize = target.size();
                
                
                boolean allDeriveEpsilon = true;
                for (String sym : p.rhs) {
                    Set<String> symFirst = firstSets.containsKey(sym) ? firstSets.get(sym) : new HashSet<>();
                    
                    for (String f : symFirst) {
                        if (!f.equals("EPSILON")) target.add(f);
                    }
                    if (!symFirst.contains("EPSILON")) {
                        allDeriveEpsilon = false;
                        break;
                    }
                }
                if (allDeriveEpsilon) target.add("EPSILON");

                if (target.size() > originalSize) changed = true;
            }
        }
        
        System.out.println("\n=== FIRST Sets ===");
        firstSets.forEach((k, v) -> {
            if(nonTerminals.contains(k)) System.out.println(k + ": " + v);
        });
    }

    private void computeFollowSets() {
        for (String nt : nonTerminals) followSets.put(nt, new HashSet<>());
        followSets.get(startSymbol).add("$"); // Add $ to start symbol

        boolean changed = true;
        while (changed) {
            changed = false;
            for (Production p : grammar) {
                for (int i = 0; i < p.rhs.size(); i++) {
                    String B = p.rhs.get(i);
                    if (!nonTerminals.contains(B)) continue;

                    Set<String> target = followSets.get(B);
                    int originalSize = target.size();

                   
                    boolean betaDerivesEpsilon = true;
                    for (int j = i + 1; j < p.rhs.size(); j++) {
                        String beta = p.rhs.get(j);
                        Set<String> firstBeta = firstSets.getOrDefault(beta, new HashSet<>());
                        for (String f : firstBeta) {
                            if (!f.equals("EPSILON")) target.add(f);
                        }
                        if (!firstBeta.contains("EPSILON")) {
                            betaDerivesEpsilon = false;
                            break;
                        }
                    }

                    // If beta is empty or derives epsilon, add Follow(A) to Follow(B)
                    if (betaDerivesEpsilon) {
                        target.addAll(followSets.get(p.lhs));
                    }

                    if (target.size() > originalSize) changed = true;
                }
            }
        }

        System.out.println("\n=== FOLLOW Sets ===");
        followSets.forEach((k, v) -> System.out.println(k + ": " + v));
    }

    // --- 3. Canonical Collection of LR(0) Items ---

    private Set<Item> closure(Set<Item> items) {
        Set<Item> closureSet = new HashSet<>(items);
        boolean changed = true;
        while (changed) {
            changed = false;
            Set<Item> temp = new HashSet<>();
            for (Item item : closureSet) {
                if (item.dotPosition < item.production.rhs.size()) {
                    String symbol = item.production.rhs.get(item.dotPosition);
                    if (nonTerminals.contains(symbol)) {
                        for (Production p : grammar) {
                            if (p.lhs.equals(symbol)) {
                                Item newItem = new Item(p, 0);
                                if (!closureSet.contains(newItem) && !temp.contains(newItem)) {
                                    temp.add(newItem);
                                    changed = true;
                                }
                            }
                        }
                    }
                }
            }
            closureSet.addAll(temp);
        }
        return closureSet;
    }

    private Set<Item> gotoState(Set<Item> items, String symbol) {
        Set<Item> nextItems = new HashSet<>();
        for (Item item : items) {
            if (item.dotPosition < item.production.rhs.size()) {
                if (item.production.rhs.get(item.dotPosition).equals(symbol)) {
                    nextItems.add(new Item(item.production, item.dotPosition + 1));
                }
            }
        }
        return closure(nextItems);
    }

    private void buildCanonicalCollection() {
        // Start with S' -> . E
        Set<Item> startState = new HashSet<>();
        startState.add(new Item(grammar.get(0), 0));
        canonicalCollection.add(closure(startState));

        boolean changed = true;
        while (changed) {
            changed = false;
            int currentSize = canonicalCollection.size();
            for (int i = 0; i < currentSize; i++) {
                Set<Item> state = canonicalCollection.get(i);
                Set<String> symbols = new HashSet<>();
                // Collect all symbols immediately after dots
                for (Item item : state) {
                    if (item.dotPosition < item.production.rhs.size()) {
                        symbols.add(item.production.rhs.get(item.dotPosition));
                    }
                }

                for (String sym : symbols) {
                    Set<Item> nextState = gotoState(state, sym);
                    if (!nextState.isEmpty() && !canonicalCollection.contains(nextState)) {
                        canonicalCollection.add(nextState);
                        changed = true;
                    }
                }
            }
        }
        
        System.out.println("\n=== Canonical Collection (States) ===");
        for(int i=0; i<canonicalCollection.size(); i++) {
            System.out.println("I" + i + ": " + canonicalCollection.get(i));
        }
    }

    // --- 4. Build Parsing Table ---

    private void buildParsingTable() {
        // 1. Initialize Tables
        for (int i = 0; i < canonicalCollection.size(); i++) {
            actionTable.put(i, new HashMap<>());
            gotoTable.put(i, new HashMap<>());
        }

        // 2. Populate Tables (Logic remains the same)
        for (int i = 0; i < canonicalCollection.size(); i++) {
            Set<Item> state = canonicalCollection.get(i);

            for (Item item : state) {
                if (item.dotPosition < item.production.rhs.size()) {
                    String sym = item.production.rhs.get(item.dotPosition);
                    if (terminals.contains(sym)) {
                        Set<Item> nextState = gotoState(state, sym);
                        int nextStateIndex = canonicalCollection.indexOf(nextState);
                        if (nextStateIndex != -1) {
                            actionTable.get(i).put(sym, "S" + nextStateIndex);
                        }
                    } 
                } else {
                    if (item.production.lhs.equals("S'")) {
                        actionTable.get(i).put("$", "ACC");
                    } else {
                        int prodIndex = grammar.indexOf(item.production);
                        for (String follow : followSets.get(item.production.lhs)) {
                            actionTable.get(i).put(follow, "R" + prodIndex);
                        }
                    }
                }
            }
            for (String nt : nonTerminals) {
                Set<Item> nextState = gotoState(state, nt);
                int nextStateIndex = canonicalCollection.indexOf(nextState);
                if (nextStateIndex != -1) {
                    gotoTable.get(i).put(nt, nextStateIndex);
                }
            }
        }

        // 3. PRINT FORMATTED TABLE
        printFormattedTable();
    }

    private void printFormattedTable() {
        System.out.println("\n=== SLR Parsing Table ===");
        
        // Prepare columns
        List<String> actionCols = new ArrayList<>(terminals);
        Collections.sort(actionCols); // Sort for readability
        actionCols.add("$"); // Add EOF to actions

        List<String> gotoCols = new ArrayList<>(nonTerminals);
        gotoCols.remove("S'"); // Don't show S' in Goto table
        Collections.sort(gotoCols);

        // Calculate spacing
        int colWidth = 10;
        String format = "%-" + colWidth + "s";
        
        // Print Header
        System.out.print(String.format(format, "STATE"));
        System.out.print("|  "); // Separator
        for (String s : actionCols) System.out.print(String.format(format, s));
        System.out.print("|  "); // Separator
        for (String s : gotoCols) System.out.print(String.format(format, s));
        System.out.println();

        // Print Separator Line
        int totalWidth = (actionCols.size() + gotoCols.size() + 1) * colWidth + 10;
        System.out.println("-".repeat(totalWidth));

        // Print Rows
        for (int i = 0; i < canonicalCollection.size(); i++) {
            System.out.print(String.format(format, i));
            System.out.print("|  ");
            
            // Action Part
            for (String s : actionCols) {
                String val = actionTable.get(i).getOrDefault(s, "");
                System.out.print(String.format(format, val));
            }
            
            System.out.print("|  ");
            
            // Goto Part
            for (String s : gotoCols) {
                Integer val = gotoTable.get(i).get(s);
                String valStr = (val != null) ? val.toString() : "";
                System.out.print(String.format(format, valStr));
            }
            System.out.println();
        }
        System.out.println("-".repeat(totalWidth));
    }
    // --- 5. Parsing Algorithm ---

    private String normalizeTokenType(String rawType) {
        // Fix mismatch between Lexer names and Grammar names
       if (rawType.equalsIgnoreCase("id") || rawType.equalsIgnoreCase("IDENTIFIER")) {
            return "id";
        }
        if (rawType.equalsIgnoreCase("integer") || rawType.equalsIgnoreCase("digit") || rawType.equalsIgnoreCase("INT") || rawType.equalsIgnoreCase("float")) {
            return "digit";
        }
        if (rawType.equals("+") || rawType.equalsIgnoreCase("PLUS")) {
            return "+";
        }
        if (rawType.equals("-")|| rawType.equalsIgnoreCase("MINUS")) {
            return "-";
        }
        if (rawType.equals("*") || rawType.equalsIgnoreCase("MULTIPLY")) {
            return "*";
        }
        if (rawType.equals("/")|| rawType.equalsIgnoreCase("DIVIDE")) {
            return "/";
        }
        if (rawType.equals("(")|| rawType.equalsIgnoreCase("LPAREN")) {
            return "(";
        }
        if (rawType.equals(")")|| rawType.equalsIgnoreCase("RPAREN")) {
            return ")";
        }
        if (rawType.equals("$")|| rawType.equalsIgnoreCase("EOF")) {
            return "$";
        }
        
        return rawType;
    }

   
    public void parse(List<LexicalAnalyzerUtil.Token> tokens) {
        Stack<Integer> stack = new Stack<>();
        stack.push(0);
        int ip = 0;

        // Ensure EOF token is present
        if (tokens.isEmpty() || 
            !tokens.get(tokens.size() - 1).getTokenType().toString().equalsIgnoreCase("EOF")) {
             tokens.add(new LexicalAnalyzerUtil.Token(LexicalAnalyzerUtil.TokenType.EOF, "$", 0, 0));
        }

        System.out.println("\n=== Parsing Steps ===");
        System.out.printf("%-20s %-20s %-20s\n", "Stack", "Input", "Action");

        while (true) {
            int s = stack.peek();
            LexicalAnalyzerUtil.Token token = tokens.get(ip);
            String rawType = token.getTokenType().toString(); 
            String a = normalizeTokenType(rawType); 
            
            
            if (!actionTable.containsKey(s)) {
                 System.out.println("Error: State " + s + " missing from Action Table.");
                 return;
            }
            
            if(!actionTable.get(s).containsKey(a)) {
                System.out.println("Error: Syntax Error at " + token);
                System.out.println("       State " + s + " expected one of: " + actionTable.get(s).keySet());
                System.out.println("       Received (Mapped) Type: '" + a + "'");
                return;
            }

            String action = actionTable.get(s).get(a);
            System.out.printf("%-20s %-20s %-20s\n", stack, token.getValue(), action);

            if (action.startsWith("S")) {
                int nextState = Integer.parseInt(action.substring(1));
                stack.push(nextState);
                ip++;
            } else if (action.startsWith("R")) {
                int prodIndex = Integer.parseInt(action.substring(1));
                Production p = grammar.get(prodIndex);
                // Pop items from stack equal to RHS length
                for (int i = 0; i < p.rhs.size(); i++) stack.pop();
                
                int t = stack.peek();
                stack.push(gotoTable.get(t).get(p.lhs));
                System.out.println("        >> Reduce: " + p);
            } else if (action.equals("ACC")) {
                System.out.println("ACCEPTED!");
                return;
            }
        }
    }

    public static void main(String[] args) {
        // 1. Build the Parser (Generates grammar, sets, and table once)
        SLRParser1 parser = new SLRParser1();
        
        Scanner scanner = new Scanner(System.in);
        System.out.println("\n===========================================");
        System.out.println("   SLR PARSER  ");
        System.out.println("   Grammar: E -> E+T | T, T -> T*F | F, etc.");
        System.out.println("   Type 'exit' to quit.");
        System.out.println("===========================================");

        while (true) {
            System.out.print("\nEnter Expression > ");
            if (!scanner.hasNextLine()) break;
            
            String input = scanner.nextLine().trim();
            
            if (input.isEmpty()) continue;
            if (input.equalsIgnoreCase("exit")) {
                System.out.println("Exiting...");
                break;
            }

            try {
                // 2. Tokenize User Input
                LexicalAnalyzerUtil lexer = new LexicalAnalyzerUtil(input);
                List<LexicalAnalyzerUtil.Token> tokens = lexer.tokenize();
                
                
                System.out.print("Tokens: ");
                for(LexicalAnalyzerUtil.Token t : tokens) {
                    if(t.getTokenType() != LexicalAnalyzerUtil.TokenType.EOF)
                        System.out.print(t.getValue() + " ");
                }
                System.out.println();

                // 4. Parse
                parser.parse(tokens);
                
            } catch (Exception e) {
                System.out.println("System Error: " + e.getMessage());
            }
        }
        scanner.close();
    }
}