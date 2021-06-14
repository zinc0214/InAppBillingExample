package com.zinc0214.inappbillingexample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import com.android.billingclient.api.*
import com.zinc0214.inappbillingexample.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var billingClient: BillingClient
    private lateinit var skuDetails: ArrayList<SkuDetails>

    /**
     * 구매 가능한 리스트의 아이템을 추가한다
     * Google PlayConsole 의 상품Id 와 동일하게 적어준다.
     *
     * always_item : 중복해서 무제한으로 살 수 있는 아이템
     * once_item : 1번만 살 수 있는 아이템
     */
    private val purchasableList = listOf("always_item", "bb")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        initBillingClient()
        connectToGooglePlay()
    }

    /**
     *  BillingClient 초기화
     */
    private fun initBillingClient() {
        billingClient =
            BillingClient.newBuilder(this@MainActivity).enablePendingPurchases()
                .setListener { billingResult, purchases ->

                    /**
                     * 구매 방식에 대해 처리한다. item 의 id 에 따라서
                     * purchaseAlways 또는 purchaseOnce 로 보낸다.
                     * (바텀에 결제 화면이 뜬 시점)
                     */
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                        for (purchase in purchases) {
                            val isAlwaysItem = purchase.skus.firstOrNull { it == "always_item" }
                            if (isAlwaysItem != null) {
                                purchaseAlwaysItem(purchase)
                            } else {
                                purchaseOnceItem(purchase)
                            }
                        }
                    } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                        "상품 주문이 취소되었습니다".showToast()
                    } else {
                        "구매요청이 실패했습니다 ;ㅁ; ${billingResult.responseCode}".showToast()
                    }
                }
                .build()
    }

    private fun connectToGooglePlay() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    getAcknowledgePurchasedItem()
                    queryPurchaseHistoryAsync()
                    querySkuDetails()
                    setUpViews()
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.e("TAG", "Service Disconnected.")
            }
        })
    }

    private fun querySkuDetails() {
        val params = SkuDetailsParams.newBuilder()
        params.setSkusList(purchasableList).setType(BillingClient.SkuType.INAPP)
        billingClient.querySkuDetailsAsync(params.build()) { result, skuDetails ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                if (skuDetails.isNullOrEmpty() || skuDetails.size != purchasableList.size) {
                    "앗 실패했다 ;ㅁ; 아이템 아이디가 확실한가요??".showToast()
                } else {
                    this.skuDetails = skuDetails as ArrayList<SkuDetails>
                }
            } else {
                "앗 실패했다 ;ㅁ; ${result.responseCode}".showToast()
            }
        }
    }

    private fun setUpViews() {
        binding.buyAlwaysButton.setOnClickListener { purchaseItem(0) }
        binding.buyOnceButton.setOnClickListener { purchaseItem(1) }
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
    fun queryPurchaseHistoryAsync() {
        billingClient.queryPurchaseHistoryAsync(
            BillingClient.SkuType.INAPP,
            object : PurchaseHistoryResponseListener {
                // 최근 구매한 아이템을 알고자 할 때 사용
                override fun onPurchaseHistoryResponse(
                    billingResult: BillingResult,
                    purchaseHistoryList: MutableList<PurchaseHistoryRecord>?
                ) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        if (!purchaseHistoryList.isNullOrEmpty()) {
                            purchaseHistoryList.forEach {
                                Log.d("hana", "Previous Purchase Item : ${it.originalJson}")
                            }
                        }
                    }
                }

            })
    }

    /**
     * 이미 결제한 적이 있는 아이템을 보여주지 않아야 하는 경우 또는
     * 선택을 비활성화 시켜야 하는 경우 등에 사용할 function
     * pusrchases list 을 통해 구매된 제품만 확인할 수 있다.
     */
    fun getAcknowledgePurchasedItem() {
        billingClient.queryPurchasesAsync(
            BillingClient.SkuType.INAPP
        ) { result, pusrchases ->
            if (pusrchases.isNullOrEmpty()) {
                Log.d("TAG", "No existing in app purchases found.")
            } else {
                pusrchases.forEach { acknowledgePurchase ->
                    purchasableList.forEach { itemId ->
                        val isPurchasedId = acknowledgePurchase.skus.firstOrNull { it == itemId }
                        if (isPurchasedId != null) {
                            binding.buyOnceButton.text = "YOU ALREADY BOUGHT...!"
                            binding.buyOnceButton.isEnabled = false
                        }
                    }
                }
            }
        }
    }


    /**
     * 아이템을 구매하기 위해서는 구매가능한 아이템 리스트와 확인이 필요하다.
     * 리스트가 존재할 경우 실제 구매를 할 수 있다.
     */
    private fun purchaseItem(itemIndex: Int) {
        val flowParams =
            BillingFlowParams.newBuilder().setSkuDetails(skuDetails[itemIndex]).build()
        val responseCode =
            billingClient.launchBillingFlow(this@MainActivity, flowParams).responseCode
        if (responseCode == BillingClient.BillingResponseCode.OK) {
            // 인앱결제 바텀시트가 노출됨
        } else {
            // 구매요청이 실패한 경우
            "구매요청이 실패했습니다 ;ㅁ; $responseCode".showToast()
        }
    }


    // 소비성 (계속 구매 가능한) 제품 구매시
    private fun purchaseAlwaysItem(purchase: Purchase) {

        // 만약에 기존에 같은 토큰값으로 구매한 경우가 있는지 확인한다.
        // 토큰값은 고유한 값이기 때문에 구매한 이력이 있다면 구매자격을 부여하지 않도록 한다.
        // *함정 : 토큰값을 저장할 수 있는 곳은 백엔드이다.  (서버개발자님을 구해보자 아님 파이어베이스라도...?)
        val consumeParams =
            ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()

        billingClient.consumeAsync(consumeParams) { billingResult, _ ->
            Log.e("ayhan : billingReulst", "$billingResult")
            "${billingResult.responseCode} 121212".showToast()
            binding.buyAlwaysButton.text = "121212"
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                "감사합니다! 사,사,사는동안 많이버세요!".showToast()
            } else {
                "구매요청이 실패했습니다 ;ㅁ; ${billingResult.responseCode}".showToast()
            }
        }
    }

    // 일회성 제품 구매시
    private fun purchaseOnceItem(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                billingClient.acknowledgePurchase(params) { billingResult ->
                    "${billingResult.responseCode}".showToast()
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        "감사합니다! 사,사,사는동안 많이버세요!".showToast()
                        queryPurchaseHistoryAsync()
                    } else {
                        "구매요청이 실패했습니다 ;ㅁ; ${billingResult.responseCode}".showToast()
                    }
                }
            }
        }
    }


    private fun String.showToast() {
        Toast.makeText(this@MainActivity, this, Toast.LENGTH_SHORT).show()
    }


}