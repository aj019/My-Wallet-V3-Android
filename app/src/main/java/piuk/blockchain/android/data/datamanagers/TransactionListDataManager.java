package piuk.blockchain.android.data.datamanagers;

import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import info.blockchain.wallet.multiaddr.MultiAddrFactory;
import info.blockchain.wallet.payload.Account;
import info.blockchain.wallet.payload.LegacyAddress;
import info.blockchain.wallet.payload.PayloadManager;
import info.blockchain.wallet.transaction.Transaction;
import info.blockchain.wallet.transaction.Tx;
import info.blockchain.wallet.transaction.TxMostRecentDateComparator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.reactivex.Observable;
import piuk.blockchain.android.data.rxjava.RxBus;
import piuk.blockchain.android.data.rxjava.RxUtil;
import piuk.blockchain.android.data.services.TransactionDetailsService;
import piuk.blockchain.android.data.stores.TransactionListStore;
import piuk.blockchain.android.util.ListUtil;

public class TransactionListDataManager {

    public final static int INDEX_ALL_REAL = -100;
    public final static int INDEX_IMPORTED_ADDRESSES = -200;
    private PayloadManager payloadManager;
    private TransactionDetailsService transactionDetails;
    private TransactionListStore transactionListStore;
    private MultiAddrFactory multiAddrFactory;
    private RxBus rxBus;

    public TransactionListDataManager(PayloadManager payloadManager,
                                      TransactionDetailsService transactionDetails,
                                      TransactionListStore transactionListStore,
                                      MultiAddrFactory multiAddrFactory,
                                      RxBus rxBus) {
        this.payloadManager = payloadManager;
        this.transactionDetails = transactionDetails;
        this.transactionListStore = transactionListStore;
        this.multiAddrFactory = multiAddrFactory;
        this.rxBus = rxBus;
    }

    /**
     * Generates a list of transactions for a specific {@link Account} or {@link LegacyAddress}.
     * The list will be sorted by date.
     *
     * @param object Either a {@link Account} or a {@link LegacyAddress}
     */
    public void generateTransactionList(Object object) {
        transactionListStore.clearList();
        if (object instanceof Account) {
            // V3
            transactionListStore.insertTransactions(getV3Transactions((Account) object));
        } else if (object instanceof LegacyAddress) {
            // V2
            transactionListStore.insertTransactions(MultiAddrFactory.getInstance().getAddressLegacyTxs(((LegacyAddress) object).getAddress()));
        } else {
            Log.e(TransactionListDataManager.class.getSimpleName(), "getBtcBalance: " + object);
            return;
        }

        transactionListStore.sort(new TxMostRecentDateComparator());
        rxBus.emitEvent(List.class, transactionListStore.getList());
    }

    /**
     * Returns a list of {@link Tx} objects generated by {@link #getTransactionList()}
     *
     * @return A list of Txs sorted by date.
     */
    @NonNull
    public List<Tx> getTransactionList() {
        return transactionListStore.getList();
    }

    /**
     * Resets the list of Transactions.
     */
    public void clearTransactionList() {
        transactionListStore.clearList();
    }

    /**
     * Allows insertion of a single new {@link Tx} into the main transaction list.
     *
     * @param transaction A new, most likely temporary {@link Tx}
     * @return An updated list of Txs sorted by date
     */
    @NonNull
    public List<Tx> insertTransactionIntoListAndReturnSorted(Tx transaction) {
        transactionListStore.insertTransactionIntoListAndSort(transaction);
        return transactionListStore.getList();
    }

    /**
     * Get total BTC balance from an {@link Account} or {@link LegacyAddress}.
     *
     * @param object Either a {@link Account} or a {@link LegacyAddress}
     * @return A BTC value as a double.
     */
    public double getBtcBalance(Object object) {
        // Update Balance
        double balance = 0.0D;
        if (object instanceof Account) {
            // V3
            Account account = ((Account) object);
            // V3 - All
            if (account.getRealIdx() == INDEX_ALL_REAL) {
                if (payloadManager.getPayload().isUpgraded()) {
                    // Balance = all xpubs + all legacy address balances
                    balance = ((double) MultiAddrFactory.getInstance().getXpubBalance())
                            + ((double) MultiAddrFactory.getInstance().getLegacyActiveBalance());
                } else {
                    // Balance = all legacy address balances
                    balance = ((double) MultiAddrFactory.getInstance().getLegacyActiveBalance());
                }
            } else if (account.getRealIdx() == INDEX_IMPORTED_ADDRESSES) {
                balance = ((double) MultiAddrFactory.getInstance().getLegacyActiveBalance());
            } else {
                // V3 - Individual
                if (MultiAddrFactory.getInstance().getXpubAmounts().containsKey(account.getXpub())) {
                    HashMap<String, Long> xpubAmounts = MultiAddrFactory.getInstance().getXpubAmounts();
                    Long bal = (xpubAmounts.get(account.getXpub()) == null ? 0L : xpubAmounts.get(account.getXpub()));
                    balance = ((double) (bal));
                }
            }
        } else if (object instanceof LegacyAddress) {
            // V2
            LegacyAddress legacyAddress = ((LegacyAddress) object);
            balance = MultiAddrFactory.getInstance().getLegacyBalance(legacyAddress.getAddress());
        } else {
            Log.e(TransactionListDataManager.class.getSimpleName(), "getBtcBalance: " + object);
            return balance;
        }

        return balance;
    }

    /**
     * Get a specific {@link Transaction} from a {@link Tx} hash.
     *
     * @param transactionHash The hash of the transaction to be returned
     * @return A Transaction object
     */
    public Observable<Transaction> getTransactionFromHash(String transactionHash) {
        return transactionDetails.getTransactionDetailsFromHash(transactionHash);
    }

    /**
     * Get a specific {@link Tx} from a hash
     *
     * @param transactionHash The hash of the Tx to be returned
     * @return An Observable object wrapping a Tx. Will call onError if not found with a
     * NullPointerException
     */
    public Observable<Tx> getTxFromHash(String transactionHash) {
        return Observable.create(emitter -> {
            //noinspection Convert2streamapi
            for (Tx tx : getTransactionList()) {
                if (tx.getHash().equals(transactionHash)) {
                    if (!emitter.isDisposed()) {
                        emitter.onNext(tx);
                        emitter.onComplete();
                    }
                    return;
                }
            }

            if (!emitter.isDisposed()) emitter.onError(new NullPointerException("Tx not found"));
        });
    }

    /**
     * Update notes for a specific transaction hash and then sync the payload to the server
     *
     * @param transactionHash The hash of the transaction to be updated
     * @param notes           Transaction notes
     * @return If save was successful
     */
    public Observable<Boolean> updateTransactionNotes(String transactionHash, String notes) {
        payloadManager.getPayload().getTransactionNotesMap().put(transactionHash, notes);
        return Observable.fromCallable(() -> payloadManager.savePayloadToServer())
                .compose(RxUtil.applySchedulersToObservable());
    }

    private List<Tx> getV3Transactions(Account account) {
        List<Tx> transactions = new ArrayList<>();

        if (account.getRealIdx() == INDEX_ALL_REAL) {
            if (payloadManager.getPayload().isUpgraded()) {
                transactions.addAll(getAllXpubAndLegacyTxs());
            } else {
                transactions.addAll(multiAddrFactory.getLegacyTxs());
            }

        } else if (account.getRealIdx() == INDEX_IMPORTED_ADDRESSES) {
            // V3 - Imported Addresses
            transactions.addAll(multiAddrFactory.getLegacyTxs());
        } else {
            // V3 - Individual
            String xpub = account.getXpub();
            if (multiAddrFactory.getXpubAmounts().containsKey(xpub)) {
                ListUtil.addAllIfNotNull(transactions, multiAddrFactory.getXpubTxs().get(xpub));
            }
        }

        return transactions;
    }

    @SuppressWarnings("Convert2streamapi")
    @VisibleForTesting
    @NonNull
    List<Tx> getAllXpubAndLegacyTxs() {
        // Remove duplicate txs
        HashMap<String, Tx> consolidatedTxsList = new HashMap<>();

        List<Tx> allXpubTransactions = multiAddrFactory.getAllXpubTxs();
        for (Tx tx : allXpubTransactions) {
            if (!consolidatedTxsList.containsKey(tx.getHash()))
                consolidatedTxsList.put(tx.getHash(), tx);
        }

        List<Tx> allLegacyTransactions = multiAddrFactory.getLegacyTxs();
        for (Tx tx : allLegacyTransactions) {
            if (!consolidatedTxsList.containsKey(tx.getHash()))
                consolidatedTxsList.put(tx.getHash(), tx);
        }

        return new ArrayList<>(consolidatedTxsList.values());
    }
}
