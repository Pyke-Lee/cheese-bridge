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
/후원 <"숲"/"치지직"> <player> <amount>
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