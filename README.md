# Cheese-Bridge

## 주의사항
```
로컬서버를 8080 포트를 이용해서 열기 때문에 다른 프로그램에서 8080 포트를 사용 중이면 문제가 발생할 수 있습니다.
```

## 명령어
```
/후원연동 | 치지직 로그인 창이 뜨며 약관 동의 시 연결이 됩니다. (방송 화면에 주소 노출 금지)
/연동해제 | 연결된 연결을 해제합니다. /후원연동 입력시 재연결됩니다.

- 관리자 전용 -
/후원 <player> <"숲"/"치지직"> <amount>
```

## Config
```cheese_bridge.json
{
    "chzzk": {
        "chzzk": {
        "clientID": "YOUR_CLIENT_ID",
        "clientSecret": "YOUR_CLIENT_SECRET"
    },
    "soop": {
        "clientID": "YOUR_CLIENT_ID",
        "clientSecret": "YOUR_CLIENT_SECRET"
    }
}
```

## 빌드 (멀티버전)
[Stonecutter](https://stonecutter.kikugie.dev/) 로 1.20.1 ~ 26.3 을 하나의 소스에서 빌드합니다.

- 빌드 대상 버전: `settings.gradle.kts` 의 `versions(...)`
- 버전별 Java / Fabric API / 호환 범위: `build.gradle.kts` 의 `BuildConfig.TARGETS`
- 컴파일 결과(바이트코드)가 같은 버전끼리 묶어 그룹의 가장 낮은 버전으로 빌드합니다 (1.20.1 / 1.20.2~1.20.4 / 1.20.5~1.21.4 / 1.21.5~1.21.10 / 1.21.11 / 26.1~26.2 / 26.3). 마인크래프트 API 를 새로 쓰는 코드를 추가했다면 그룹 안의 다른 버전도 잠시 목록에 넣어 빌드해 jar 클래스가 같은지 확인하세요.
- 버전별 코드 분기: `//? if >=1.21 {` 형태의 Stonecutter 주석 (소스는 `stonecutter active` 버전 기준으로 작성됨)
- Gradle 데몬은 Java 25 로 실행됩니다 (`gradle/gradle-daemon-jvm.properties`). 각 버전 컴파일에는 JDK 17 / 21 / 25 가 필요하며 없으면 자동으로 받습니다.

```
./gradlew build            # 전체 버전 빌드 → build/mods/
./gradlew :1.21.1:build    # 특정 버전만 빌드
```

## 치지직 API 애플리케이션 등록 방법
https://developers.chzzk.naver.com/

<img src="./chzzk_app_auth.png">

## SOOP API 발급
https://developers.sooplive.co.kr/

## kubejs 사용법 예시
```
// server_scripts/donation_handler.js
const DonationReceivedCallback = Java.loadClass('kr.pyke.integration.event.DonationReceivedCallback')
const PLATFORM = Java.loadClass('kr.pyke.type.PLATFORM')
const ItemStack = Java.loadClass('net.minecraft.world.item.ItemStack')
const Items = Java.loadClass('net.minecraft.world.item.Items')
const EntityType = Java.loadClass('net.minecraft.world.entity.EntityType')
const MobEffectInstance = Java.loadClass('net.minecraft.world.effect.MobEffectInstance')
const MobEffects = Java.loadClass('net.minecraft.world.effect.MobEffects')
const MobSpawnType = Java.loadClass('net.minecraft.world.entity.MobSpawnType')

DonationReceivedCallback.registerHandler((player, event) => {
    let name = player.getDisplayName().getString()
    let platform = event.platform()
    let sender = event.donor()
    let amount = event.getAmount()
    let krwAmount = amount
    let notification = ''

    if (platform == PLATFORM.SOOP) {
        krwAmount = amount * 100
        notification = `별풍선 ${amount}개`
    }
    else if (platform == PLATFORM.CHZZK) {
        notification = `${amount} 치즈`
    }

    let rewardText = null

    // 1,000원
    if (krwAmount == 1000) {
        let stack = new ItemStack(Items.COOKED_BEEF, 3)
        if (player.addItem(stack)) {
            player.drop(stack, false)
        }
        rewardText = '구운 소고기 3개'
    }
    // 3,000원
    else if (krwAmount == 3000) {
        let level = player.serverLevel()
        let pos = player.blockPosition()
        EntityType.ZOMBIE.spawn(level, pos, MobSpawnType.EVENT)
        rewardText = '좀비 소환'
    }

    if (rewardText) {
        player.tell(Component.literal(`§a${sender}§r님이 §b${name}§r님에게 §e${notification}§r로 §6[ ${rewardText} ]§r를 후원합니다.`))
    }
})
```

## MOD 개발자 사용법
```
repositories {
	maven { url = uri("https://jitpack.io") }
}
```
```
dependencies {
	modImplementation("com.github.erudites-dev:CheeseBridge:1.3.5-1.20.1")
}
```
```
DonationReceivedCallback.DONATION_RECEIVED.register((player, event) -> {
    String name = player.getDisplayName().getString();  // 연동된 플레이어 이름
    PLATFORM platform = event.platfor(); // 후원 플랫폼 SOOP/CHZZK
    String sender = event.donor(); // 후원자
    String message = event.donationMessage(); // 후원 메시지
    int amount = event.getAmount(); // 금액 (숲: 별풍선 갯수/치지직: 치즈)
    int krwAmount = amount; // 금액(원 기준)
    String notification = "";

    if (platform == PLATFORM.SOOP) {
        krwAmount *= 100;
        notification = String.format("별풍선 %,d개", amount);
    }
    else if (platform == PLATFORM.CHZZK) {
        notification = String.format("%,d 치즈", amount);
    }

    // 5천원 (50개)
    if (5000 == krwAmount) {
        ItemStack itemStack = new ItemStack(Items.BREAD);
        itemStack.setCount(3);

        if (!player.addItem(itemStack)) {
            player.drop(itemStack, false);
        }
        player.sendSystemMessage(Component.literal(String.format("§a%s§r님이 §b%s§r님에게 §e%s§r로 §6[ 빵 3개 ]§r를 후원합니다.", sender, name, notification)));
        // 시청자님이 스트리머님에게 5,000 치즈로 [ 빵 3개 ]를 후원합니다.
        // 시청자님이 스트리머님에게 별풍선 50개로 [ 빵 3개 ]를 후원합니다.
    }
});
```
