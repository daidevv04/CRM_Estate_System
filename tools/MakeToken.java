import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Ky access token HS256 bang JWT_SECRET - dung de test API can xac thuc khi khong
 * biet mat khau tai khoan. Claim giong het AuthService phat hanh:
 *   iss=user-service, sub=<user id>, username, role, iat, exp
 * (KHONG co claim token_type: chi refresh token moi co claim do).
 *
 * java tools/MakeToken.java <base64-secret> <user-id> <username> <role> [ttl-giay]
 */
public class MakeToken {

    public static void main(String[] args) throws Exception {
        String secret = args[0];
        String userId = args[1];
        String username = args[2];
        String role = args[3];
        long ttlSeconds = args.length > 4 ? Long.parseLong(args[4]) : 900;

        Instant now = Instant.now();
        String header = "{\"alg\":\"HS256\"}";
        String payload = String.format(
                "{\"iss\":\"user-service\",\"iat\":%d,\"exp\":%d,\"sub\":\"%s\",\"username\":\"%s\",\"role\":\"%s\"}",
                now.getEpochSecond(), now.plusSeconds(ttlSeconds).getEpochSecond(), userId, username, role);

        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String signingInput = encoder.encodeToString(header.getBytes(StandardCharsets.UTF_8))
                + "." + encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256"));
        String signature = encoder.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));

        System.out.println(signingInput + "." + signature);
    }
}
