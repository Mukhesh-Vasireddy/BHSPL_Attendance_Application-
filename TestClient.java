import java.net.*;
import java.io.*;
import java.util.*;

public class TestClient {
    public static void main(String[] args) throws Exception {
        CookieManager cookieManager = new CookieManager();
        CookieHandler.setDefault(cookieManager);

        URL loginUrl = new URL("http://localhost:8080/login");
        HttpURLConnection con = (HttpURLConnection) loginUrl.openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        String postData = "username=admin&password=user";
        try (OutputStream os = con.getOutputStream()) {
            os.write(postData.getBytes());
            os.flush();
        }
        int code = con.getResponseCode();
        System.out.println("Login code: " + code);

        // Then get system/users
        URL usersUrl = new URL("http://localhost:8080/system/users");
        HttpURLConnection con2 = (HttpURLConnection) usersUrl.openConnection();
        con2.setRequestMethod("GET");
        int code2 = con2.getResponseCode();
        System.out.println("Users code: " + code2);
        
        try (BufferedReader br = new BufferedReader(new InputStreamReader(con2.getInputStream()))) {
            String line;
            int count = 0;
            while ((line = br.readLine()) != null) {
                System.out.println(line);
                count++;
                if(count > 30) {
                    System.out.println("... truncated");
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
