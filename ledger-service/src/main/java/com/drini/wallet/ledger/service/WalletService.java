package com.drini.wallet.ledger.service;

import com.drini.wallet.ledger.api.dto.BalanceResponse;
import com.drini.wallet.ledger.api.dto.WalletResponse;
import com.drini.wallet.ledger.domain.Account;
import com.drini.wallet.ledger.domain.AccountType;
import com.drini.wallet.ledger.repository.AccountRepository;
import com.drini.wallet.ledger.repository.PendingTransferRepository;
import com.drini.wallet.common.error.ErrorCode;
import com.drini.wallet.common.error.WalletException;
import com.drini.wallet.common.money.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Creates wallets and reads balances. */
@Service
public class WalletService {

    private final AccountRepository accounts;
    private final PendingTransferRepository pendingTransfers;

    public WalletService(AccountRepository accounts, PendingTransferRepository pendingTransfers) {
        this.accounts = accounts;
        this.pendingTransfers = pendingTransfers;
    }

    @Transactional
    public UUID createWalletIfMissing(UUID ownerId, String userAccountType, String displayName) {
        Optional<UUID> existing = accounts.findWalletIdByOwnerId(ownerId);
        if (existing.isPresent()) {
            return existing.get();
        }
        String walletType = AccountType.walletFor(userAccountType).name();
        accounts.insertWalletIfMissing(UUID.randomUUID(), ownerId, walletType, displayName);
        return accounts.findWalletIdByOwnerId(ownerId).orElseThrow();
    }

    @Transactional
    public BalanceResponse getBalance(UUID ownerId, String userAccountType, String displayName) {
        UUID walletId = createWalletIfMissing(ownerId, userAccountType, displayName);
        Account wallet = accounts.findById(walletId).orElseThrow();

        BalanceResponse response = new BalanceResponse();
        response.setWalletId(walletId);
        response.setAccountType(wallet.getType().name());
        response.setCurrency(wallet.getCurrency());
        response.setAvailableBalance(wallet.getBalance());
        response.setPendingIncoming(Money.normalize(pendingTransfers.sumPending(walletId, "INFLOW")));
        response.setPendingOutgoing(Money.normalize(pendingTransfers.sumPending(walletId, "OUTFLOW")));
        response.setPendingBalance(response.getPendingIncoming().add(response.getPendingOutgoing()));
        return response;
    }

    @Transactional(readOnly = true)
    public WalletResponse getWalletByOwner(UUID ownerId) {
        UUID walletId = accounts.findWalletIdByOwnerId(ownerId)
                .orElseThrow(() -> new WalletException(ErrorCode.WALLET_NOT_FOUND, "This user has no wallet"));
        return getWallet(walletId);
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(UUID walletId) {
        Optional<Account> wallet = accounts.findById(walletId);
        if (wallet.isEmpty() || wallet.get().getOwnerId() == null) {
            throw new WalletException(ErrorCode.WALLET_NOT_FOUND, "Wallet not found");
        }
        WalletResponse response = new WalletResponse();
        response.setWalletId(wallet.get().getId());
        response.setOwnerId(wallet.get().getOwnerId());
        response.setAccountType(wallet.get().getType().name());
        response.setBalance(wallet.get().getBalance());
        return response;
    }
}
