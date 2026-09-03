package com.calc.expense

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * 앱의 진짜 시작점. 구글 로그인이 없으면 여기서 막고, 있으면 곧장 [HomeActivity] 로 넘긴다.
 *
 * 지금까지 챌린지 탭은 기기마다 다른 익명 uid 를 썼다 — 재설치하면 uid 가 바뀌어 데이터가
 * 끊겼다. 로그인을 앱 진입 자체의 문으로 두면 [FirebaseAuth.getCurrentUser] 가 앱 전체(챌린지
 * 포함)에서 같은 구글 계정 uid 로 통일된다 — 별도 신원 통합 코드 없이 자동으로 맞춰진다.
 */
class LoginActivity : ComponentActivity() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (auth.currentUser != null) {
            goHome()
            return
        }

        setContent {
            var signingIn: Boolean by remember { mutableStateOf(false) }
            var errorMessage: String? by remember { mutableStateOf(null) }
            val scope = rememberCoroutineScope()

            LoginScreen(
                signingIn = signingIn,
                errorMessage = errorMessage,
                onSignIn = {
                    if (signingIn) return@LoginScreen
                    signingIn = true
                    errorMessage = null
                    scope.launch {
                        val result: Result<Unit> = signInWithGoogle()
                        signingIn = false
                        result
                            .onSuccess { goHome() }
                            .onFailure { errorMessage = "로그인에 실패했어요. 다시 시도해 주세요." }
                    }
                },
            )
        }
    }

    private suspend fun signInWithGoogle(): Result<Unit> {
        return try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getString(R.string.default_web_client_id))
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val response = CredentialManager.create(this).getCredential(this, request)
            val credential = response.credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return Result.failure(IllegalStateException("지원하지 않는 로그인 방식이에요"))
            }
            val idToken: String = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(firebaseCredential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun goHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}

@Composable
private fun LoginScreen(
    signingIn: Boolean,
    errorMessage: String?,
    onSignIn: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = HomePalette.Ground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "지출 기록",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = HomePalette.Ink,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "구글 계정으로 로그인하면\n곳간 기록이 안전하게 보관돼요",
                fontSize = 15.sp,
                color = HomePalette.Ink2,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(40.dp))
            Button(
                onClick = onSignIn,
                enabled = !signingIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HomePalette.AccentBright,
                    disabledContainerColor = HomePalette.Chip,
                ),
            ) {
                if (signingIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(text = "구글로 로그인", fontSize = 16.sp, color = Color.White)
                }
            }
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = errorMessage, fontSize = 13.sp, color = HomePalette.Over)
            }
        }
    }
}
