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
import com.google.firebase.auth.FirebaseAuth
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

    /** 계정에 맡겨 둔 개인 설정을 되찾는 중. 버튼 자리에 도는 표시를 띄운다. */
    private var restoring: Boolean by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (auth.currentUser != null) {
            goNext()
            // 되찾을 것이 없으면 goNext 가 이미 다음 화면으로 넘겼다. 되찾는 중이면 아래 화면을 그린다.
            if (isFinishing) return
        }

        setContent {
            var signingIn: Boolean by remember { mutableStateOf(false) }
            var errorMessage: String? by remember { mutableStateOf(null) }
            val scope = rememberCoroutineScope()

            LoginScreen(
                signingIn = signingIn || restoring,
                errorMessage = errorMessage,
                onSignIn = {
                    if (signingIn) return@LoginScreen
                    signingIn = true
                    errorMessage = null
                    scope.launch {
                        val result: Result<Unit> = signInWithGoogle()
                        signingIn = false
                        result
                            .onSuccess { goNext() }
                            .onFailure { errorMessage = tr("로그인에 실패했어요. 다시 시도해 주세요.", "Sign-in failed. Please try again.", "No se pudo iniciar sesión. Vuelve a intentarlo.") }
                    }
                },
            )
        }
    }

    private suspend fun signInWithGoogle(): Result<Unit> {
        return try {
            auth.signInWithCredential(GoogleCredentials.fetch(this, onlyAuthorized = false)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 로그인 뒤 도착할 곳. 처음 쓰는 사람은 «챌린지 금액 정하기»부터 지나간다.
     *
     * 규칙은 [Onboarding.shouldShow] 하나가 갖는다 — 여기서 조건을 다시 적으면 «한 번
     * 물어봤으면 다시 묻지 않는다»가 조용히 깨진다.
     */
    private fun goNext() {
        // 계정이 바뀌었으면 앞사람의 예산·고정비·곳간을 먼저 비운다. 이걸 빼먹으면 새 계정이
        // 남의 챌린지 금액을 물려받고, «이미 물어봤다»로 온보딩까지 건너뛴다.
        AccountScope.syncTo(this, auth.currentUser?.uid)

        if (!onboardingNeeded()) {
            route()
            return
        }
        // 이 폰에는 예산이 없다 — 처음 쓰는 사람이거나 다시 설치한 사람이다. 계정에 맡겨 둔
        // 개인 설정이 있으면 되찾아 온보딩을 건너뛴다([PersonalBackupSync]).
        restoring = true
        PersonalBackupSync.restoreIfEmpty(this) {
            if (isFinishing || isDestroyed) return@restoreIfEmpty
            restoring = false
            route()
        }
    }

    private fun onboardingNeeded(): Boolean = Onboarding.shouldShow(
        wasAsked = FixedCostStore.wasAsked(this),
        hasBudget = SettingsStore.load(this).personal.hasBudget,
    )

    private fun route() {
        startActivity(if (onboardingNeeded()) OnboardingActivity.firstRun(this) else Intent(this, HomeActivity::class.java))
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
                text = tr("하루치", "Haruchi", "Haruchi"),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = HomePalette.Ink,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = tr(
                    "구글 계정으로 로그인하면\n기록이 계정에 저장돼요",
                    "Sign in with Google\nto keep your records safe",
                    "Inicia sesión con Google\npara guardar tus gastos de forma segura",
                ),
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
                    Text(text = tr("구글로 로그인", "Sign in with Google", "Iniciar sesión con Google"), fontSize = 16.sp, color = Color.White)
                }
            }
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = errorMessage, fontSize = 13.sp, color = HomePalette.Over)
            }
        }
    }
}
