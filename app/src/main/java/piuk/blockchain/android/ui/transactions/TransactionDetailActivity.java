package piuk.blockchain.android.ui.transactions;

import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.ColorRes;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatEditText;
import android.support.v7.widget.Toolbar;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import info.blockchain.wallet.multiaddr.MultiAddrFactory;

import java.util.List;
import java.util.Locale;

import piuk.blockchain.android.R;
import piuk.blockchain.android.databinding.ActivityTransactionDetailsBinding;
import piuk.blockchain.android.ui.base.BaseAuthActivity;
import piuk.blockchain.android.ui.customviews.ToastCustom;
import piuk.blockchain.android.ui.receive.RecipientAdapter;
import piuk.blockchain.android.util.ViewUtils;
import piuk.blockchain.android.util.annotations.Thunk;

public class TransactionDetailActivity extends BaseAuthActivity implements TransactionDetailViewModel.DataListener {

    @Thunk ActivityTransactionDetailsBinding mBinding;
    private TransactionDetailViewModel mViewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_transaction_details);
        mViewModel = new TransactionDetailViewModel(this);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar_general);
        setupToolbar(toolbar, R.string.transaction_detail_title);

        mBinding.editIcon.setOnClickListener(v -> mBinding.descriptionField.performClick());
        mBinding.descriptionField.setOnClickListener(v -> {
            AppCompatEditText editText = new AppCompatEditText(this);
            editText.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE
                    | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE);
            editText.setHint(R.string.transaction_detail_description_hint);

            int maxLength = 256;
            InputFilter[] fArray = new InputFilter[1];
            fArray[0] = new InputFilter.LengthFilter(maxLength);
            editText.setFilters(fArray);
            editText.setText(mViewModel.getTransactionNote());
            editText.setSelection(editText.getText().length());

            new AlertDialog.Builder(this, R.style.AlertDialogStyle)
                    .setTitle(R.string.app_name)
                    .setView(ViewUtils.getAlertDialogEditTextLayout(this, editText))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        mViewModel.updateTransactionNote(editText.getText().toString());
                        setDescription(editText.getText().toString());
                    })
                    .show();
        });

        mViewModel.onViewReady();
    }

    @Override
    public void setTransactionType(String type) {
        switch (type) {
            case MultiAddrFactory.MOVED:
                mBinding.transactionType.setText(getResources().getString(R.string.MOVED));
                break;
            case MultiAddrFactory.RECEIVED:
                mBinding.transactionType.setText(getResources().getString(R.string.RECEIVED));
                mBinding.transactionFee.setVisibility(View.GONE);
                break;
            case MultiAddrFactory.SENT:
                mBinding.transactionType.setText(getResources().getString(R.string.SENT));
                break;
        }
    }

    @Override
    public void onDataLoaded() {
        mBinding.mainLayout.setVisibility(View.VISIBLE);
        mBinding.loadingLayout.setVisibility(View.GONE);
    }

    @Override
    public void setTransactionColour(@ColorRes int colour) {
        mBinding.transactionAmount.setTextColor(ResourcesCompat.getColor(getResources(), colour, getTheme()));
        mBinding.transactionType.setTextColor(ResourcesCompat.getColor(getResources(), colour, getTheme()));
    }

    @Override
    public void setTransactionValueBtc(String value) {
        mBinding.transactionAmount.setText(value);
    }

    @Override
    public void setTransactionValueFiat(String fiat) {
        mBinding.transactionValue.setText(fiat);
    }

    @Override
    public void setToAddresses(List<RecipientModel> addresses) {
        if (addresses.size() == 1) {
            mBinding.toAddress.setText(addresses.get(0).getAddress());
        } else {
            mBinding.spinner.setVisibility(View.VISIBLE);
            RecipientAdapter adapter = new RecipientAdapter(addresses);
            mBinding.toAddress.setText(String.format(Locale.getDefault(), "%1s Recipients", addresses.size()));
            mBinding.toAddress.setOnClickListener(v -> mBinding.spinner.performClick());
            mBinding.spinner.setAdapter(adapter);
            mBinding.spinner.setOnItemSelectedListener(null);
        }
    }

    @Override
    public void setDate(String date) {
        mBinding.date.setText(date);
    }

    @Override
    public void setDescription(String description) {
        mBinding.descriptionField.setText(description);
    }

    @Override
    public void setFromAddress(String address) {
        mBinding.fromAddress.setText(address);
    }

    @Override
    public void setStatus(String status, String hash) {
        mBinding.status.setText(status);
        mBinding.buttonVerify.setOnClickListener(v -> {
            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            viewIntent.setData(Uri.parse("https://blockchain.info/tx/" + mViewModel.getTransactionHash()));
            startActivity(viewIntent);
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_transaction_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_share:
                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.putExtra(Intent.EXTRA_TEXT, "https://blockchain.info/tx/" + mViewModel.getTransactionHash());
                shareIntent.setType("text/plain");
                startActivity(Intent.createChooser(shareIntent, getString(R.string.transaction_detail_share_chooser)));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void setIsDoubleSpend(boolean isDoubleSpend) {
        if (isDoubleSpend) {
            mBinding.doubleSpendWarning.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void showToast(@StringRes int message, @ToastCustom.ToastType String toastType) {
        ToastCustom.makeText(this, getString(message), ToastCustom.LENGTH_SHORT, toastType);
    }

    @Override
    public void setFee(String fee) {
        mBinding.transactionFee.setText(String.format(Locale.getDefault(), getString(R.string.transaction_detail_fee), fee));
    }

    @Override
    public void pageFinish() {
        finish();
    }

    @Override
    public Intent getPageIntent() {
        return getIntent();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mViewModel.destroy();
    }
}
