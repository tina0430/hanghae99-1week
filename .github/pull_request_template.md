### **커밋 링크**
<!--


필수 양식)
커밋 이름 : 커밋 링크
테스트 코드 작성 : a8672f6
기능 구현 : ccc3ee4
-->

---
### **리뷰 포인트(질문)**
- 각 단계별 목적에 맞는 테스트 코드를 작성하려 노력했습니다.
  - 단위 테스트의 경우, 다른 모듈과의 연동을 최대한 배제하고 테스트 대상의 책임/역할에만 집중했습니다.
  - 통합 테스트의 경우, 단위 테스트에서 검증한 내용을 중복해서 검증하지 않았습니다. 다만, 단위 테스트로 넣기 애매한 경우에는 통합 테스트의 정상 기능을 통해 해당 기능이 정상 작동하는지 체크했습니다. (e.g. ~Table 클래스) 
- 최대 충전 금액은 정책에 따라 변경될 수 있으므로, 상수로 별도 분리하고, 서비스 클래스에서 주입받도록 하여 외부에서 유연하게 관리할 수 있도록 설계.
<!-- - 리뷰어가 특히 확인해야 할 부분이나 신경 써야 할 코드가 있다면 명확히 작성해주세요.(최대 2개)
  
  좋은 예:
  - 예외를 핸들링하는 방법에 대한 피드백 부탁드립니다.
  - 필수로 작성했어야 했던 테스트 케이스가 더 있을까요?

-->
---
### **이번주 KPT 회고**

### Keep
새로운 지식을 공부했습니다.

### Problem
시간을 정해놓고 학습하지 못했습니다....

### Try
야근으로 인해 학습 시간을 지키지 못하는 경우가 발생하니, 아침에 공부하겠습니다...