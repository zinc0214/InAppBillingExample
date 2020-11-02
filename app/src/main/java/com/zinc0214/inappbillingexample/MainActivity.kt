package com.zinc0214.inappbillingexample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import com.android.billingclient.api.*
import com.zinc0214.inappbillingexample.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(),
    PurchasesUpdatedListener,
    PurchaseHistoryResponseListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var billingClient: BillingClient
    private val purchasableList = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        initBillingClient()
    }
    /**
     *  BillingClient 초기화
     */
    private fun initBillingClient() {
        billingClient = BillingClient.newBuilder(this).enablePendingPurchases().setListener(this)
            .build()
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    getAcknowledgePurchasedItem()
                    getAllPurchasedItem()
                    setPurchasableList()
                    setUpViews()
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.e("TAG", "Service Disconnected.")
            }
        })
    }

    /**
     * 이미 결제한 적이 있는 아이템을 보여주지 않아야 하는 경우 또는
     * 선택을 비활성화 시켜야 하는 경우 등에 사용할 function
     * acknowledgePurchase 을 통해 구매된 제품만 확인할 수 있다.
     */
    fun getAcknowledgePurchasedItem() {

        // BillingClient 의 준비가 되지않은 상태라면 돌려보낸다
        if (!billingClient.isReady) {
            return
        }

        // 인앱결제된 내역을 확인한다
        val result = billingClient.queryPurchases(BillingClient.SkuType.INAPP)
        if (result.purchasesList == null) {
            Log.d("TAG", "No existing in app purchases found.")
        } else {
            Log.d("TAG", "Existing Once Type Item Bought purchases: ${result.purchasesList}")
            result.purchasesList?.forEach {
                if (it.sku == "one_item") {
                    binding.buyOnceButton.text = "YOU ALREADY BOUGHT...!"
                    binding.buyOnceButton.isEnabled = false
                }
            }
        }
    }

    /**
     * 최근에 결제한 아이템을 확인하는 목적
     * checkPurchaseHistory function 과 다르게  consumeAsync 를 통해 구매된 상품도 확인할 수 있다.
     * !!주의!! 아이템 Id 로만 가져오기 때문에 여러번 구매하더라도 가장 최근의 제품만 가져온다.
     * 예를 들어 다음과 같다.
     *   1. always_item 과 one_item 를 20.10.10 에 구매했다. 각 token 는 aaa, bbb 였다.
     *   2. 최근 구매 내역으로 [ always_item (token=aaa) , one_item (token=bbb) ] 가 노출된다. onPurchaseHistoryResponse 에서 확인할 수 있다.
     *   3. always_item 을 20.10.15 에 구매했다. 이때의 token 은 ccc 였다.
     *   4. 다시 onPurchaseHistoryResponse 를 호출하면 최근구매내역이 [ always_item (token=ccc), one_item (token=bbb) ] 가 노출된다.
     *
     *   즉 동일한 Id 의 상품은 덮어씌워진다.
     */
    fun getAllPurchasedItem() {
        billingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.INAPP, this)
    }

    /**
     * 구매 가능한 리스트의 아이템을 추가한다.
     */
    private fun setPurchasableList() {

        // Google PlayConsole 의 상품Id 와 동일하게 적어준다
        purchasableList.add("always_item") // 중복해서 무제한으로 살 수 있는 아이템
        purchasableList.add("one_item") // 1번만 살 수 있는 아이템
    }


    private fun setUpViews() {
        binding.buyOnceButton.setOnClickListener { purchaseItem(1) }
        binding.buyAlwaysButton.setOnClickListener { purchaseItem(0) }
    }


    /**
     * 아이템을 구매하기 위해서는 구매가능한 아이템 리스트와 확인이 필요하다.
     * 리스트가 존재할 경우 실제 구매를 할 수 있다.
     */
    private fun purchaseItem(listNumber: Int) {
        val params = SkuDetailsParams.newBuilder()
        params.setSkusList(purchasableList).setType(BillingClient.SkuType.INAPP)
        billingClient.querySkuDetailsAsync(params.build()) { result, skuDetails ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && !skuDetails.isNullOrEmpty()) {
                val flowParams =
                    BillingFlowParams.newBuilder().setSkuDetails(skuDetails[listNumber]).build()
                billingClient.launchBillingFlow(this, flowParams)
            } else {
                Log.e("TAG", "No sku found from query")
            }
        }
    }

    /**
     * 구매 방식에 대해 처리한다. item 의 id 에 따라서
     * purchaseAlways 또는 purchaseOnce 로 보낸다.
     * (바텀에 결제 화면이 뜬 시점)
     */
    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchaseList: MutableList<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchaseList?.forEach {
                    if (it.sku == "always_item") {
                        purchaseAlways(it.purchaseToken)
                    } else {
                        purchaseOnce(it.purchaseToken)
                    }

                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d("TAG", "You've cancelled the Google play billing process...")
            }
            else -> {
                Log.e(
                    "TAG",
                    "Item not found or Google play billing error... : ${billingResult.responseCode}"
                )
            }
        }
    }

    // 최근 구매한 아이템을 알고자 할 때 사용
    override fun onPurchaseHistoryResponse(
        billingResult: BillingResult,
        purchaseHistoryList: MutableList<PurchaseHistoryRecord>?
    ) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            if (!purchaseHistoryList.isNullOrEmpty()) {
                // 가장 최근에 구매된 아이템을 확인할 수 있다.
                purchaseHistoryList.forEach {
                    Log.d("TAG", "Previous Purchase Item : ${it.originalJson}")
                }
            }
        }
    }

    // 소비성 (계속 구매 가능한) 제품 구매시
    private fun purchaseAlways(purchaseToken: String) {
        val consumeParams = ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build()

        billingClient.consumeAsync(consumeParams) { billingResult, _ ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                "구매가 성공했습니다! 1 ".showToast()
            } else {
                Log.e("TAG", "FAIL : ${billingResult.responseCode}")
            }
        }
    }

    // 일회성 제품 구매시
    private fun purchaseOnce(purchaseToken: String) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                "구매가 성공했습니다!".showToast()
            } else {
                Log.e("TAG", "FAIL : ${billingResult.responseCode}")
            }
        }
    }

    private fun String.showToast() {
        Toast.makeText(this@MainActivity, this, Toast.LENGTH_SHORT).show()
    }
}