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

DonationReceivedCallback.DONATION_RECEIVED.register((player, event) => {
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
        player.getInventory().add(stack)
        rewardText = '구운 소고기 3개'
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
