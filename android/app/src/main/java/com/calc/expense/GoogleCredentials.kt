package com.calc.expense

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.GoogleAuthProvider

/**
 * 구글 계정 고르기 창을 띄워 Firebase 에 넘길 자격을 받는다.
 *
 * 로그인([LoginActivity])과 계정 삭제([AccountDeletion])가 같이 쓴다 — 삭제는 Firebase 가
 * «방금 로그인했는지»를 요구해서, 지우기 직전에 같은 창으로 한 번 더 확인받는다.
 */
object GoogleCredentials {

    /**
     * @param onlyAuthorized true 면 이 앱에 이미 쓴 계정만 보인다(재확인용). 로그인은 false.
     */
    suspend fun fetch(activity: Activity, onlyAuthorized: Boolean): AuthCredential {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(onlyAuthorized)
            .setServerClientId(activity.getString(R.string.default_web_client_id))
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val response = CredentialManager.create(activity).getCredential(activity, request)
        val credential = response.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw IllegalStateException(tr("지원하지 않는 로그인 방식이에요", "Unsupported sign-in method", "Método de inicio de sesión no compatible"))
        }
        val idToken: String = GoogleIdTokenCredential.createFrom(credential.data).idToken
        return GoogleAuthProvider.getCredential(idToken, null)
    }
}
