package com.drini.wallet.auth.domain;

import com.drini.wallet.common.error.WalletException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Checks how emails and phone numbers are cleaned up before we look users up. */
class IdentifierTest {

    @Test
    void emailIsLowerCasedAndTrimmed() {
        Identifier identifier = Identifier.parse("  Ana@Wallet.LOCAL ");

        assertThat(identifier.isEmail()).isTrue();
        assertThat(identifier.getValue()).isEqualTo("ana@wallet.local");
    }

    @Test
    void phoneIsNormalizedToE164() {
        Identifier identifier = Identifier.parse("+355 (69) 123-4567");

        assertThat(identifier.isPhone()).isTrue();
        assertThat(identifier.getValue()).isEqualTo("+355691234567");
    }

    @Test
    void invalidValuesAreRefused() {
        assertThatThrownBy(() -> Identifier.parse("0691234567")).isInstanceOf(WalletException.class);
        assertThatThrownBy(() -> Identifier.parse("ana@wallet")).isInstanceOf(WalletException.class);
        assertThatThrownBy(() -> Identifier.parse(" ")).isInstanceOf(WalletException.class);
        assertThatThrownBy(() -> Identifier.parse(null)).isInstanceOf(WalletException.class);
    }

    @Test
    void userOwnsOnlyTheirIdentifier() {
        User ana = new User(java.util.UUID.randomUUID(), Identifier.parse("ana@wallet.local"), "Ana", Role.CUSTOMER,
                AccountType.PERSONAL);

        assertThat(ana.owns(Identifier.parse("ANA@wallet.local"))).isTrue();
        assertThat(ana.owns(Identifier.parse("besi@wallet.local"))).isFalse();
        assertThat(ana.owns(Identifier.parse("+355691234567"))).isFalse();
    }
}
