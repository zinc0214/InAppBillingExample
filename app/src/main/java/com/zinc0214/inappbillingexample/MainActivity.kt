package com.zinc0214.inappbillingexample

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ConsumeResponseListener
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchaseHistoryParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.zinc0214.inappbillingexample.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var billingClient: BillingClient
    private lateinit var productDetails: ArrayList<ProductDetails>
    private val alreadyBoughtPurchase = mutableListOf<Purchase>()
    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        /**
         * 구매 방식에 대해 처리한다. item 의 id 에 따라서
         * purchaseAlways 또는 purchaseOnce 로 보낸다.
         * (바텀에 결제 화면이 뜬 시점)
         */
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                val isAlwaysItem = purchase.products.firstOrNull { it == "always_item" }
                if (isAlwaysItem != null) {
                    purchaseAlwaysItem(purchase)
                } else {
                    //  purchaseOnceItem(purchase)
                }
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            "상품 주문이 취소되었습니다".showToast()
        } else {
            "구매요청이 실패했습니다 ;ㅁ; ${billingResult.responseCode}".showToast()
        }
    }


    /**
     * 구매 가능한 리스트의 아이템을 추가한다
     * Google PlayConsole 의 상품Id 와 동일하게 적어준다.
     *
     * always_item : 중복해서 무제한으로 살 수 있는 아이템
     * once_item : 1번만 살 수 있는 아이템
     */
    private val productIdList = listOf("always_item", "att")
    private val productList = listOf(
        QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productIdList[0])
            .setProductType(BillingClient.ProductType.INAPP)
            .build(),
        QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productIdList[1])
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        initBillingClient()
        connectToGooglePlay()
    }

    // BillingClient 초기화 6.0 & 대기 중인 거래 처리
    private fun initBillingClient() {
        billingClient =
            BillingClient.newBuilder(this@MainActivity).enablePendingPurchases()
                .setListener(purchasesUpdatedListener)
                .build()
    }

    override fun onResume() {
        super.onResume()
        CoroutineScope(Dispatchers.Main).launch {
            queryPurchaseAsync()
        }
    }

    private fun connectToGooglePlay() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    CoroutineScope(Dispatchers.Main).launch {
                        val job1 = async { queryPurchaseHistoryAsync() }
                        val job2 = async { queryPurchaseAsync() }
                        val job3 = async { queryProductDetails() }
                        joinAll(job1, job2, job3)
                        setUpViews()
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.e("TAG", "Service Disconnected.")
            }
        })
    }

    // 6.0 기본
    private fun queryProductDetails2() {
        val params = QueryProductDetailsParams.newBuilder()
        params.setProductList(productList).build()

        billingClient.queryProductDetailsAsync(params.build()) { result, productDetailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                if (productDetailsList.isEmpty()) {
                    "앗 실패했다 ;ㅁ; 아이템 아이디가 정확한가요?".showToast()
                } else {
                    this.productDetails = productDetailsList as ArrayList<ProductDetails>
                }
            } else {
                "앗 실패했다 ;ㅁ; 상품 정보를 가져올 수 없어요. ${result.responseCode}".showToast()
            }
        }
    }

    // 6.0 ktx
    private suspend fun queryProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
        params.setProductList(productList).build()

        withContext(Dispatchers.IO) {
            billingClient.queryProductDetails(params.build())
        }.runCatching {
            productDetails = productDetailsList as ArrayList<ProductDetails>
        }.getOrElse {
            "앗 실패했다 ;ㅁ; 상품 정보를 가져올 수 없어요. ${it.message}".showToast()
        }
    }

    private fun setUpViews() {
        binding.buyAlwaysButton.setOnClickListener {
            startPurchaseFlow(0)
        }
        binding.buyOnceButton.setOnClickListener {
            startPurchaseFlow(1)
        }
        binding.useOnceButton.setOnClickListener {
            purchaseOnceItem(alreadyBoughtPurchase[0])
        }
    }


    /**
     * 네트워크 문제 , 앱 외부에서 구매 처리등
     * 유저의 구매내역을 확인하는 방법.
     *
     * 구매가 만료, 취소, 소비된 경우에도 유저의 가장 최근 구매를 반환한다
     *
     * 최근에 결제한 아이템을 확인하는 목적.
     *
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
        val params = QueryPurchaseHistoryParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)

        billingClient.queryPurchaseHistoryAsync(params.build()) { result, purchaseRecord ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                // Do Something
            }
        }
    }

    /**
     * INAPP 의 경우
     *  - 구매한 상품 중 "소비된" 비소비성 상품만 확인할 수 있다.
     *
     * SUBS 의 경우
     *  - 활성 정기 결제 및 미사용 일회성 구매만 반환 한다.
     */
    suspend fun queryPurchaseAsync() {

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)

        billingClient.queryPurchasesAsync(params.build()).apply {
            val billingResult = this.billingResult
            val purchasedList = this.purchasesList

            // 최근 구매한 아이템을 알고자 할 때 사용
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {

                // 비소시성 아이템의 id 가 구매한 id 와 일치하는지 확인
                val boughtOnceItems =
                    purchasedList.filter { it.products.firstOrNull { productId -> productId == productIdList[1] } != null }

                boughtOnceItems.forEach { purchase ->
                    if (purchase.isAcknowledged) {
                        binding.useOnceButton.text = "YOU ALREADY USED...!"
                        binding.useOnceButton.isEnabled = false
                    }
                    binding.buyOnceButton.text = "YOU ALREADY BOUGHT...!"
                    binding.buyOnceButton.isEnabled = false

                    alreadyBoughtPurchase.add(purchase)
                }
            }
        }
    }

    private fun startPurchaseFlow(itemIndex: Int) {
        val purchaseDetail = productDetails[itemIndex].oneTimePurchaseOfferDetails
        if (purchaseDetail == null) {
            "앗 실패했다 ;ㅁ; 아이템 아이디가 확실한가요??".showToast()
            return
        }
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails[itemIndex])
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val billingResult = billingClient.launchBillingFlow(this, billingFlowParams)

        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            "구매요청이 실패했습니다 ;ㅁ; ${billingResult.responseCode}".showToast()
        }
    }

    // 소비성 (계속 구매 가능한) 제품 구매시
    private fun purchaseAlwaysItem(purchase: Purchase) {
        // 만약에 기존에 같은 토큰값으로 구매한 경우가 있는지 확인한다.
        // 토큰값은 고유한 값이기 때문에 구매한 이력이 있다면 구매자격을 부여하지 않도록 한다.
        // *함정 : 토큰값을 저장할 수 있는 곳은 백엔드이다.  (서버개발자님을 구해보자 아님 파이어베이스라도...?)
        val consumeParams =
            ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()

        billingClient.consumeAsync(consumeParams, object : ConsumeResponseListener {
            override fun onConsumeResponse(result: BillingResult, p1: String) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    CoroutineScope(Dispatchers.Main).launch {
                        val billingResult =
                            billingClient.consumePurchase(consumeParams).billingResult
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            "감사합니다! 상품은 소비되었지만, 또 구매할 수 있답니다!".showToast()
                        } else {
                            "구매요청이 실패했습니다 ;ㅁ; ${billingResult.responseCode}".showToast()
                        }
                    }
                }
            }
        })
    }

    // 일회성 제품 구매시
    private fun purchaseOnceItem(purchase: Purchase) {

        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken).build()

        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                !purchase.isAcknowledged
            ) {
                CoroutineScope(Dispatchers.Main).launch {
                    val ackPurchaseResult =
                        billingClient.acknowledgePurchase(acknowledgePurchaseParams)

                    if (ackPurchaseResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        "감사합니다! 상품은 소비되었으며 더 이상 구매할 수 없습니당!".showToast()
                        queryPurchaseAsync()

                    } else {
                        "구매요청이 실패했습니다 ;ㅁ; ${ackPurchaseResult.responseCode}".showToast()
                    }
                }
            }
        }
    }

    private fun String.showToast() {
        Toast.makeText(this@MainActivity, this, Toast.LENGTH_SHORT).show()
    }
}