package fr.mecadetect.securite;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/** La clé de signature HS256 est contrôlée au démarrage : une clé faible arrête l'application. */
class CleJwtTest {

    private final SecurityConfig configuration = new SecurityConfig();

    private static String base64(int octets) {
        return Base64.getEncoder().encodeToString(new byte[octets]);
    }

    @Test
    void accepte_une_cle_de_256_bits() {
        var cle = configuration.cleJwt(base64(32));

        assertThat(cle.getAlgorithm()).isEqualTo("HmacSHA256");
        assertThat(cle.getEncoded()).hasSize(32);
    }

    @Test
    void refuse_une_cle_de_moins_de_256_bits() {
        assertThatIllegalStateException()
                .isThrownBy(() -> configuration.cleJwt(base64(31)))
                .withMessageContaining("256 bits");
    }

    @Test
    void refuse_une_cle_qui_n_est_pas_en_base64() {
        assertThatIllegalArgumentException().isThrownBy(() -> configuration.cleJwt("pas du base64 !"));
    }
}