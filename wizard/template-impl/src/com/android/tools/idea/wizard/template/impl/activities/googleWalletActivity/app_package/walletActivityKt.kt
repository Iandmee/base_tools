/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.wizard.template.impl.activities.googleWalletActivity.app_package

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.escapeKotlinIdentifier
import com.android.tools.idea.wizard.template.impl.activities.common.importViewBindingClass
import com.android.tools.idea.wizard.template.impl.activities.common.layoutToViewBindingClass
import com.android.tools.idea.wizard.template.renderIf

fun walletActivityKt(
  activityClass: String,
  layoutName: String,
  packageName: String,
  applicationPackage: String?,
  isViewBindingSupported: Boolean
): String {
  val contentViewBlock = if (isViewBindingSupported) """
      // Use view binding to access the UI elements
      layout = ${layoutToViewBindingClass(layoutName)}.inflate(layoutInflater)
      setContentView(layout.root)
  """ else "setContentView(R.layout.$layoutName)"

  val googleWalletButtonBlock = if (isViewBindingSupported)
    "addToGoogleWalletButton = layout.addToGoogleWalletButton.root"
  else "addToGoogleWalletButton = findViewById<View>(R.id.addToGoogleWalletButton)"

  return """
package ${escapeKotlinIdentifier(packageName)}

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.gms.pay.Pay
import com.google.android.gms.pay.PayApiAvailabilityStatus
import com.google.android.gms.pay.PayClient
import java.util.*

${importViewBindingClass(isViewBindingSupported, packageName, applicationPackage, layoutName, Language.Kotlin)}

/**
 * An Activity that allows the user to add a pass to the user's Google Wallet using the
 * Google Wallet Android SDK.
 *
 * If you are testing out the new Wallet functionality, you will need to create a
 * [Temporary Issuer Account](https://wallet-lab-tools.web.app/issuers) prior to running
 * this example. If you are using the temporary issuer account, you must enter the TODO fields
 * in the [WalletActivity.onAddToWalletClicked] function.
 *
 * If you are using this template to create a custom pass, you will need to sign into the Google Pay
 * Business Console, Create a New Pass, and Alter the JSON in the validator to ensure the pass looks
 * and behaves as preferred. For further instruction, please follow
 * [the provided documentation](https://developers.google.com/wallet/generic/android/prerequisites).
 *
 * @see Pay.getClient
 * @see PayClient.savePasses
 * @see SamplePass
 */
class $activityClass : AppCompatActivity() {

    companion object {
        /**
         * The Request Code we pass along to the Google Wallet API when attempting to add a pass.
         */
        private const val ADD_TO_GOOGLE_WALLET_REQUEST_CODE = 1000
    }

${renderIf(isViewBindingSupported){ """
    private lateinit var layout: ${layoutToViewBindingClass(layoutName)}
""" }}
    private lateinit var addToGoogleWalletButton: View

    /**
     * The [PayClient] which we use to interact with the Google Wallet API.
     */
    private lateinit var walletClient: PayClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Instantiate the Pay client
        walletClient = Pay.getClient(this)

        $contentViewBlock

        $googleWalletButtonBlock

        // Attach a click listener to the "Add To Wallet" button
        addToGoogleWalletButton.setOnClickListener { onAddToWalletClicked() }

        // Trigger the API availability request.
        fetchCanUseGoogleWalletApi()
    }

    /**
     * A helper method that allows us to check if the Google Wallet API is available on the current
     * device.
     *
     * Please note, some countries do not have Google Wallet available to them yet.
     */
    private fun fetchCanUseGoogleWalletApi() {
        walletClient
            .getPayApiAvailabilityStatus(PayClient.RequestType.SAVE_PASSES)
            .addOnSuccessListener { status ->
                // Display the "Add to Wallet" button if the wallet API is available on this device.
                addToGoogleWalletButton.isVisible = (status == PayApiAvailabilityStatus.AVAILABLE)
            }
            .addOnFailureListener {
                // Hide the button and optionally show an error message
                addToGoogleWalletButton.isVisible = false

                Toast.makeText(
                    this,
                    R.string.google_wallet_status_unavailable,
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    /**
     * Called when the user clicks the "Add to Wallet" button.
     *
     * See https://wallet-lab-tools.web.app/issuers to create a temporary issuer account for testing
     * Google Wallet functionality.
     */
    private fun onAddToWalletClicked() {
        // Create a SamplePass object with your unique fields
        val samplePass = SamplePass(
            issuerEmail = "",   // TODO(you) – Enter issuer email address,
            issuerId = "",      // TODO(you) – Enter issuer ID
            passClass = "",     // TODO(you) – Enter pass class
            passId = UUID.randomUUID().toString()
        )

        // Call to the Wallet API to save the pass to the user's wallet.
        walletClient.savePasses(samplePass.toJson, this, ADD_TO_GOOGLE_WALLET_REQUEST_CODE)
    }

    /**
     * Handle the result from [PayClient.savePasses], where we check to see if our attempt to add
     * the pass to the user's Google Wallet was successful, or not.
     *
     * It is up to the user to handle edge/cases
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != ADD_TO_GOOGLE_WALLET_REQUEST_CODE) {
            return
        }

        when (resultCode) {
            // We successfully added the pass to Google Wallet!
            RESULT_OK -> {
                Toast.makeText(
                    this,
                    R.string.add_google_wallet_success,
                    Toast.LENGTH_SHORT
                ).show()
            }
            // Occurs when the user cancelled the flow.
            RESULT_CANCELED -> {
                Toast.makeText(
                    this,
                    R.string.add_google_wallet_cancelled,
                    Toast.LENGTH_SHORT
                ).show()
            }
            // A known error occurred when attempting to save the pass to Google Wallet.
            PayClient.SavePassesResult.SAVE_ERROR -> data?.let { intentData ->
                // Handle the error message and optionally display it to the user.
                val errorMessage = intentData.getStringExtra(PayClient.EXTRA_API_ERROR_MESSAGE)
                Toast.makeText(
                    this,
                    errorMessage,
                    Toast.LENGTH_SHORT
                )
            }
            // An unknown error occurred when attempting to add to Google Wallet.
            else -> {
                Toast.makeText(
                    this,
                    R.string.add_google_wallet_unknown_error,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
  """
}