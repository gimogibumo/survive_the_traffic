import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.springframework.security.crypto.bcrypt.BCrypt;

// Invoke from phase8_aws.py: input and output are captured in memory, never logged.
class Phase8PasswordHash {
    public static void main(String[] args) throws Exception {
        String password = new BufferedReader(new InputStreamReader(System.in)).readLine();
        System.out.print(BCrypt.hashpw(password, BCrypt.gensalt(10)));
    }
}
