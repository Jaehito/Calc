package com.calc.expense

import android.content.Context

/** 가정 설정을 받아 본 결과. */
enum class HouseholdPull {
    /** 묶이지 않았거나 읽지 못했다. 로컬은 그대로다. */
    SKIPPED,

    /** 가정 문서에 같이 쓰는 설정이 아직 없다. 로컬은 그대로다. */
    EMPTY,

    /** 가정 값과 로컬이 같았다. */
    SAME,

    /** 가정 값으로 로컬을 덮었다. */
    CHANGED,
}

/**
 * 같이 쓰는 설정([SharedSettings])을 가정 문서와 이 폰 사이에서 옮긴다. 규칙은
 * [HouseholdSettings] 가 갖고, 여기는 저장소를 부르는 일만 한다.
 *
 * - 받기([pull]): 홈·설정을 열 때. 가정 값이 로컬과 다르면 로컬을 덮는다.
 * - 올리기([pushIfNeeded]): 설정을 저장할 때. 같이 쓰는 값이 바뀌었거나 가정 문서가 비었을 때만.
 *
 * 묶이지 않았으면 둘 다 아무 일도 하지 않는다. 콜백은 메인 스레드로 온다(Firestore 기본).
 */
object HouseholdSync {

    /**
     * 재설치 등으로 **로컬 가정 id 가 비었으면** 저장소(`users/{uid}.householdId`)에서 되찾고,
     * 가정 설정(공용 예산·월급날·공용 이름)까지 받아 온다. 되찾았으면 true 로 알린다.
     *
     * 예전에는 설정 화면만 이걸 해서, 다시 설치한 사람의 홈에 공용 곳간이 설정에 한 번 다녀올
     * 때까지 안 보였다. 홈이 열릴 때 부른다. 콜백은 메인 스레드로 온다.
     */
    fun restoreIfMissing(context: Context, onDone: (Boolean) -> Unit) {
        val app: Context = context.applicationContext
        if (HouseholdStore.householdId(app) != null) return onDone(false)
        val uid: String = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: return onDone(false)
        HouseholdRepository.currentHouseholdId(uid) { id ->
            // 가정이 없거나 읽지 못했다(둘 다 null). 다음에 홈을 열 때 다시 묻는다 — 문서 하나라 가볍다.
            if (id == null || HouseholdStore.householdId(app) != null) return@currentHouseholdId onDone(false)
            HouseholdStore.setHouseholdId(app, id)
            pull(app) { onDone(true) }
        }
    }

    fun pull(context: Context, onDone: (HouseholdPull) -> Unit = {}) {
        val app: Context = context.applicationContext
        val id: String = HouseholdStore.householdId(app) ?: return onDone(HouseholdPull.SKIPPED)
        HouseholdRepository.readSettings(id) { result ->
            // 읽는 사이 가정을 옮겼으면 앞 가정의 값이다.
            if (HouseholdStore.householdId(app) != id || result.isFailure) {
                return@readSettings onDone(HouseholdPull.SKIPPED)
            }

            val remote: SharedSettings? = result.getOrNull()
            HouseholdStore.setSettingsMissing(app, remote == null)
            if (remote == null) return@readSettings onDone(HouseholdPull.EMPTY)

            val local: Settings = SettingsStore.load(app)
            if (!HouseholdSettings.differs(local, remote)) return@readSettings onDone(HouseholdPull.SAME)
            SettingsStore.save(app, HouseholdSettings.apply(local, remote))
            onDone(HouseholdPull.CHANGED)
        }
    }

    /** 설정을 [before] 에서 [after] 로 저장했다(안 바뀌었어도 부른다). 올려야 하면 올린다. */
    fun pushIfNeeded(context: Context, before: Settings, after: Settings) {
        val app: Context = context.applicationContext
        if (HouseholdStore.householdId(app) == null) return
        if (!HouseholdSettings.shouldPush(before, after, HouseholdStore.isSettingsMissing(app))) return
        push(app, after)
    }

    /** 조건 없이 올린다. 가정을 막 만들었을 때 — 배우자가 들어오자마자 받을 값이 있어야 한다. */
    fun push(context: Context, settings: Settings) {
        val app: Context = context.applicationContext
        val id: String = HouseholdStore.householdId(app) ?: return
        HouseholdRepository.writeSettings(id, HouseholdSettings.of(settings)) { result ->
            if (result.isSuccess && HouseholdStore.householdId(app) == id) {
                HouseholdStore.setSettingsMissing(app, false)
            }
        }
    }
}
