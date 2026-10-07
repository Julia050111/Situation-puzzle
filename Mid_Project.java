import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.io.*;
import java.nio.file.*;

/**
 * 專案名稱：午夜終端 (Midnight Terminal) — 雲端題庫海龜湯推理系統
 * 開發者：鐘怡婷 (1121703)、翁榆涵 (1121553)
 * 特色：GitHub 雲端動態題庫、串流打字機、多模型切換、自動存檔、真相提取
 */
public class Mid_Project {

    // ==========================================
    // 0. 題目資料結構
    // ==========================================
    record Puzzle(String title, String soupFace, String soupBottom) {}

    // ==========================================
    // 1. UI 視覺效果與 ANSI 色彩模組
    // ==========================================
    static class UI {
        public static final String RESET  = "\033[0m";
        public static final String CYAN   = "\033[36m";   // AI 語氣
        public static final String GREEN  = "\033[32m";  // 玩家輸入
        public static final String PURPLE = "\033[35m"; // 標題/系統
        public static final String RED    = "\033[31m";    // 警告/錯誤
        public static final String YELLOW = "\033[33m"; // 操作提示

        public static void printLogo() {
            String logo = """
                 _____   _   _   ____   _____  _      _____ 
                /  ___| | | | | |  _ \\ |_   _|| |    |  ___|
                \\ `--.  | | | | | |_) |  | |  | |    | |__  
                 `--. \\ | | | | |  _ <   | |  | |    |  __| 
                /\\__/ / | |_| | | | | \\  | |  | |____| |___ 
                \\____/   \\___/  |_| \\_\\  |_|  \\_____/\\____/ 
                     M Y S T E R Y   S O U P   v 5 . 0 (Cloud Edition)
                """;
            System.out.println(color(logo, CYAN));
            System.out.println(color("狀態：連線至 GitHub 雲端題庫庫", PURPLE));
            System.out.println("--------------------------------------------------");
        }

        public static String color(String text, String color) {
            return color + text + RESET;
        }

        public static void printHint() {
            System.out.println(color("\n" + "=".repeat(50), YELLOW));
            System.out.println(color("[遊戲提示]", YELLOW));
            System.out.println(color("1. 請針對故事細節提問，AI 裁判只會回答『是』、『不是』或『無關』。", YELLOW));
            System.out.println(color("2. 當你覺得掌握真相時，請輸入『answer』來揭曉真相。", YELLOW));
            System.out.println(color("3. 指令：輸入 'save' 存檔，'exit' 離開。", YELLOW));
            System.out.println(color("=".repeat(50), YELLOW));
        }
    }

    // ==========================================
    // 2. 雲端題庫抓取系統 (取代原本的 ScraperSystem)
    // ==========================================
    static class CloudSoupDB {
        // ★★★ 請將這裡的網址，換成你 GitHub 上的 Raw 網址 ★★★
        private static final String CLOUD_URL = "https://raw.githubusercontent.com/yuhan0111/java_mid_project/refs/heads/main/puzzles.txt";

        public static Puzzle fetchFromCloud() {
            try {
                System.out.println(UI.color("[系統] 正在連線至雲端題庫中心...", UI.PURPLE));
                
                HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(CLOUD_URL))
                        .timeout(java.time.Duration.ofSeconds(5))
                        .GET()
                        .build();

                String content = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
                String[] lines = content.split("\n");

                if (lines.length == 0 || content.trim().isEmpty()) {
                    throw new Exception("雲端檔案內容為空或無法讀取。");
                }

                // 隨機抽一題，過濾掉空白行
                List<String> validLines = Arrays.stream(lines).filter(l -> l.contains("|")).toList();
                if (validLines.isEmpty()) throw new Exception("找不到符合格式的題目。");

                String randomLine = validLines.get(new Random().nextInt(validLines.size())).trim();
                String[] parts = randomLine.split("\\|"); 

                if (parts.length >= 3) {
                    // 如果你的文字有包含 \n (字串)，在此替換回真正的換行
                    String face = parts[1].replace("\\n", "\n");
                    String bottom = parts[2].replace("\\n", "\n");
                    return new Puzzle(parts[0], face, bottom);
                } else {
                    throw new Exception("題目格式解析失敗 (缺少分隔符號 '|')");
                }

            } catch (Exception e) {
                System.out.println(UI.color("[警告] 雲端連線失敗：" + e.getMessage(), UI.RED));
                System.out.println(UI.color("[系統] 啟動離線模式：使用本機經典題庫...", UI.YELLOW));
                
                // 斷網或網址錯誤時的備用題目
                return new Puzzle(
                    "器官", 
                    "在兒子去世一週年的忌日上，我殺死了三個來悼念他的人，為什麼？", 
                    "我的兒子生病去世，他在臨終前表示想將器官捐給需要的人，最後他身上的三個器官成功捐出。然而在兒子的忌日上，我發現接受兒子皮膚捐贈的人竟然全身刺青，接受肝捐贈的人在打電話約人喝酒，而接受肺捐贈的人不停地抽菸。憤怒之下，我殺害了他們。"
                );
            }
        }
    }

    // ==========================================
    // 3. 存檔與題目記憶模組
    // ==========================================
    static class SaveSystem {
        private static final String SAVE_FILE = "current_game_save.txt";

        public static void saveGame(List<Map<String, String>> history) {
            try (PrintWriter out = new PrintWriter(new FileWriter(SAVE_FILE))) {
                for (var m : history) {
                    out.println(m.get("role") + "|" + m.get("content").replace("\n", "\\n"));
                }
            } catch (IOException e) { System.err.println("存檔失敗: " + e.getMessage()); }
        }

        public static List<Map<String, String>> loadGame() {
            try {
                if (!Files.exists(Paths.get(SAVE_FILE))) return null;
                List<String> lines = Files.readAllLines(Paths.get(SAVE_FILE));
                List<Map<String, String>> history = new ArrayList<>();
                for (String line : lines) {
                    String[] p = line.split("\\|", 2);
                    history.add(new HashMap<>(Map.of("role", p[0], "content", p[1].replace("\\n", "\n"))));
                }
                return history;
            } catch (Exception e) { return null; }
        }
    }

    // ==========================================
    // 4. 通訊核心 (OllamaClient)
    // ==========================================
    static class OllamaClient {
        private final String model;
        private final HttpClient http = HttpClient.newHttpClient();

        public OllamaClient(String model) { this.model = model; }

        public void chatStream(List<Map<String, String>> messages, java.util.function.Consumer<String> callback) {
            try {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < messages.size(); i++) {
                    var m = messages.get(i);
                    sb.append("{\"role\":\"%s\",\"content\":\"%s\"}".formatted(m.get("role"), esc(m.get("content"))));
                    if (i < messages.size() - 1) sb.append(",");
                }
                sb.append("]");

                String body = "{\"model\":\"%s\",\"messages\":%s,\"stream\":true}".formatted(model, sb.toString());
                
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:11434/api/chat"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

                http.send(req, HttpResponse.BodyHandlers.ofLines()).body().forEach(line -> {
                    String content = extract(line, "content");
                    callback.accept(content);
                });
            } catch (Exception e) {
                System.out.println(UI.color("\n[連線錯誤] 請確認 Ollama 伺服器是否已啟動。", UI.RED));
            }
        }

        private String extract(String json, String key) {
            int i = json.indexOf("\"" + key + "\":");
            if (i < 0) return "";
            int s = json.indexOf("\"", i + key.length() + 3) + 1;
            int e = s;
            while (e < json.length()) {
                if (json.charAt(e) == '"' && json.charAt(e - 1) != '\\') break;
                e++;
            }
            return json.substring(s, e).replace("\\n", "\n").replace("\\\"", "\"");
        }

        private String esc(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        }
    }

    // ==========================================
    // 5. 主程式邏輯 (Main)
    // ==========================================
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        UI.printLogo();

        System.out.println(UI.color("請選擇核心裁判模型 (輸入 1 或 2)：", UI.PURPLE));
        System.out.println("1. Qwen3-1.7b (繁體中文邏輯強)");
        System.out.println("2. Llama3/Gemma (其他可用模型)");
        String choice = sc.nextLine();
        String modelName = choice.equals("2") ? "gemma3:1b" : "qwen3:1.7b";
        OllamaClient ai = new OllamaClient(modelName);

        List<Map<String, String>> history = SaveSystem.loadGame();
        
        if (history == null || history.isEmpty()) {
            history = new ArrayList<>();
            
            // 從 GitHub 雲端抓取題目
            Puzzle currentPuzzle = CloudSoupDB.fetchFromCloud();
            
            System.out.println(UI.color("\n【本局謎題：" + currentPuzzle.title() + "】", UI.PURPLE));
            System.out.println(UI.color(currentPuzzle.soupFace(), UI.CYAN));
            System.out.println();
            
            String systemMsg = """
                你現在是一位冷酷無情的海龜湯主持人（AI 裁判）。
                這局的遊戲已經開始，以下是絕對的真相：
                
                【湯面（玩家已知的線索）】：%s
                【湯底（隱藏的真相）】：%s
                
                【你的唯一任務】：
                1. 根據「湯底」的事實，判斷玩家的提問,且只能根據湯底的內容,不要加入其他任何思考。
                2. 面對玩家提問，你「只能」回答三個詞：『是』、『不是』、『無關』。
                3. 絕對不可以解釋原因，絕對不可以把湯底說出來！
                """.formatted(currentPuzzle.soupFace(), currentPuzzle.soupBottom());
            
            history.add(new HashMap<>(Map.of("role", "system", "content", systemMsg)));
            SaveSystem.saveGame(history);
            
        } else {
            System.out.println(UI.color("\n[系統] 檢測到殘留記憶，已重新連結至先前的謎題...", UI.CYAN));
            System.out.println(UI.color("AI 裁判 : 等待你的提問...", UI.CYAN));
        }

        // 遊戲主迴圈
        while (true) {
            UI.printHint();
            System.out.print(UI.color("\n妳的提問 > ", UI.GREEN));
            String input = sc.nextLine().trim();

            if (input.equalsIgnoreCase("exit")) {
                SaveSystem.saveGame(history);
                System.out.println("偵探生涯暫告一段落，下次見。");
                break;
            }
            if (input.equalsIgnoreCase("save")) {
                SaveSystem.saveGame(history);
                System.out.println(UI.color("存檔成功！", UI.CYAN));
                continue;
            }
            if (input.isEmpty()) continue;

            // 處理「揭曉真相」 (直接由系統提取，100% 不會出錯)
            if (input.contains("揭曉真相") || input.equalsIgnoreCase("answer")) {
                System.out.println(UI.color("\n[系統] 正在從記憶深處提取真實湯底...", UI.PURPLE));
                
                String sysMsg = history.get(0).get("content");
                String truth = "";
                String targetKey = "【湯底（隱藏的真相）】：";
                
                int startIndex = sysMsg.indexOf(targetKey);
                if (startIndex != -1) {
                    startIndex += targetKey.length();
                    int endIndex = sysMsg.indexOf("\n【", startIndex); 
                    if (endIndex == -1) endIndex = sysMsg.length();
                    truth = sysMsg.substring(startIndex, endIndex).trim();
                } else {
                    truth = "（系統無法解析歷史記憶中的真相）";
                }
                
                System.out.println(UI.color("\n==================================================", UI.YELLOW));
                System.out.println(UI.color("AI 裁判 (真相) : \n" + truth, UI.CYAN));
                System.out.println(UI.color("==================================================", UI.YELLOW));
                System.out.println(UI.color("\n[案件結案] 感謝參與這次的邏輯風暴！", UI.PURPLE));
                
                new File("current_game_save.txt").delete();
                break; 

            } else {
                // ==========================================
                // 升級版：隱藏式思維鏈 (Hidden CoT) 提問法
                // ==========================================
                // 讓 AI 先思考再下結論，大幅提升邏輯準確率
                String sysMsg = history.get(0).get("content");
                String truth = "";
                int startIndex = sysMsg.indexOf("【湯底（隱藏的真相）】：");
                if (startIndex != -1) {
                    startIndex += 13;
                    int endIndex = sysMsg.indexOf("\n【", startIndex);
                    if (endIndex == -1) endIndex = sysMsg.length();
                    truth = sysMsg.substring(startIndex, endIndex).trim();
                } else {
                    truth = "無法讀取湯底";
                }

                // 2. 打造全新、無上下文干擾的閱讀測驗指令
                String questionWithConstraint = """
                    [任務] 嚴格事實比對
                    
                    【絕對事實】：
                    %s
                    
                    【玩家猜測】：
                    %s
                    
                    【判斷準則】（請一步一步嚴格執行）：
                    1. 絕對事實中，是否有明確出現玩家猜測的「具體名詞」或「醫學狀態」（如啞巴、毒藥）？
                    2. 如果事實只寫了行為（如：不講話、倒下），但玩家猜測了具體身份或死因（如：啞巴、中毒），這屬於過度推論，【必須】判定為錯誤！
                    
                    請直接輸出最終判定，只能是以下三者之一：
                    【結論：是】
                    【結論：不是】
                    【結論：無關】
                    """.formatted(truth, input);
                
                // 3. 關鍵改動：創一個「暫時的」對話清單，只傳遞這次的閱讀測驗給 AI
                List<Map<String, String>> tempHistory = new ArrayList<>();
                tempHistory.add(new HashMap<>(Map.of("role", "user", "content", questionWithConstraint)));
                
                System.out.print(UI.color("AI 裁判 交叉比對中... ", UI.CYAN));
                StringBuilder rawResponse = new StringBuilder();
                
                // 用這個「沒有過去記憶」的暫時清單去問 AI
                ai.chatStream(tempHistory, (chunk) -> {
                    rawResponse.append(chunk);
                });
                
                String raw = rawResponse.toString();
                String finalAnswer = "";
                
                if (raw.contains("【結論：不是】") || raw.contains("結論：不是")) {
                    finalAnswer = "不是";
                } else if (raw.contains("【結論：是】") || raw.contains("結論：是")) {
                    finalAnswer = "是";
                } else if (raw.contains("【結論：無關】") || raw.contains("結論：無關")) {
                    finalAnswer = "無關";
                } else {
                    if (raw.contains("不是") || raw.contains("否")) finalAnswer = "不是";
                    else if (raw.contains("是") || raw.contains("對")) finalAnswer = "是";
                    else finalAnswer = "無關";
                }
                
                System.out.print("\r" + " ".repeat(30) + "\r");
                System.out.println(UI.color("AI 裁判 : ", UI.CYAN) + finalAnswer);
                
                // 4. 存檔時，我們只把玩家乾淨的輸入和 AI 乾淨的結論存進去，不污染 AI 記憶
                history.add(new HashMap<>(Map.of("role", "user", "content", input)));
                history.add(new HashMap<>(Map.of("role", "assistant", "content", finalAnswer)));
                
                SaveSystem.saveGame(history);
            }
        }
    }
}