import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * UR賃貸の空室チェッカー。
 *
 * 使い方:
 *   java VacancyChecker <danchi> <物件名> <物件URL>
 *
 * 例:
 *   java VacancyChecker 700 "東綾瀬パークタウン" https://www.ur-net.go.jp/chintai/kanto/tokyo/20_7000.html
 *
 * 通知先(ntfyのトピック名)は環境変数 NTFY_TOPIC から読む。
 *
 * 判定:
 *   APIレスポンスが "null" → 空室なし(何もしない)
 *   それ以外               → 空室あり(ntfyに通知)
 */
public class VacancyChecker {

    // 部屋一覧を返すURのAPI
    private static final String API_URL =
            "https://chintai.r6.ur-net.go.jp/chintai/api/bukken/detail/detail_bukken_room/";

    // 支社コード・識別区分は東綾瀬/大谷田とも共通
    private static final String SHISYA = "20";
    private static final String SHIKIBETU = "0";

    private static final String NTFY_BASE = "https://ntfy.sh/";

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("使い方: java VacancyChecker <danchi> <物件名> <物件URL>");
            System.exit(2);
        }
        String danchi = args[0];
        String name = args[1];
        String pageUrl = args[2];

        String topic = System.getenv("NTFY_TOPIC");
        if (topic == null || topic.isBlank()) {
            System.err.println("環境変数 NTFY_TOPIC が設定されていません。");
            System.exit(2);
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        boolean hasVacancy = checkVacancy(client, danchi);

        if (hasVacancy) {
            System.out.println("[" + name + "] 空室あり → 通知します");
            notify(client, topic, name, pageUrl);
        } else {
            System.out.println("[" + name + "] 空室なし");
        }
    }

    /** URのAPIを叩いて空室の有無を返す。レスポンスが "null" なら空室なし。 */
    private static boolean checkVacancy(HttpClient client, String danchi) throws Exception {
        // 注意: パラメータが欠けると空室があっても "null" が返る。
        //       ブラウザが送っている項目を一通り含める必要がある。
        String body = "shisya=" + SHISYA
                + "&danchi=" + danchi
                + "&shikibetu=" + SHIKIBETU
                + "&rent_low="
                + "&rent_high="
                + "&floorspace_low="
                + "&floorspace_high="
                + "&orderByField=0"
                + "&orderBySort=0"
                + "&pageIndex=0";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Origin", "https://www.ur-net.go.jp")
                .header("Referer", "https://www.ur-net.go.jp/")
                .header("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("API応答が異常です: HTTP " + response.statusCode());
        }

        String trimmed = response.body().trim();
        // 空室なしのときは本文が "null" になる
        return !trimmed.equalsIgnoreCase("null") && !trimmed.isEmpty();
    }

    /** ntfyに通知を送る。 */
    private static void notify(HttpClient client, String topic, String name, String pageUrl)
            throws Exception {
        String message = name + "に空室が出ました。お早めに確認を。\n" + pageUrl;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(NTFY_BASE + topic))
                .timeout(Duration.ofSeconds(20))
                .header("Title", "=?UTF-8?B?" + base64(name + "に空室あり") + "?=") // 日本語タイトル対応
                .header("Click", pageUrl)
                .POST(HttpRequest.BodyPublishers.ofString(message, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() / 100 != 2) {
            throw new RuntimeException("ntfy通知に失敗: HTTP " + response.statusCode()
                    + " / " + response.body());
        }
        System.out.println("通知を送信しました。");
    }

    /** ntfyのヘッダは非ASCIIを直接送れないのでタイトルをBase64エンコードする。 */
    private static String base64(String s) {
        return java.util.Base64.getEncoder()
                .encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
}
