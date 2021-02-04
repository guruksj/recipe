# Search Recipe

# 서비스 시나리오
## 기능적 요구사항
1. 회원이 레시피를 검색한다.
1. 회원이 검색된 내용을 보고 부족한 재료를 주문한다.
1. 주문이 되면 주문 내역에 대한 배송이 시작된다.
1. 배송 정보는 주문 정보에 업데이트 된다.
1. 고객이 주문을 취소할 수 있다.
1. 주문이 취소되면 배송도 취소된다.
1. 고객이 주문상태를 중간중간 조회한다.

## 비기능적 요구사항
1. 트랜잭션
    1. 고객이 주문을 취소하면 주문도 즉시 주문이 취소된다. → Sync 호출
1. 장애격리
    1. 배송 서비스가 정상 기능이 되지 않더라도 주문을 받을 수 있다. → Async (event-driven), Eventual Consistency
    1. 주문시스템이 과중되면 사용자를 잠시동안 받지 않고 주문을 잠시후에 하도록 유도한다 → Circuit breaker, fallback
1. 성능
    1. 고객이 배달상태를 주문시스템에서 확인할 수 있어야 한다. → CQRS 

# 체크포인트
https://workflowy.com/s/assessment/qJn45fBdVZn4atl3

# 분석/설계
## AS-IS 조직 (Horizontally-Aligned)  
![image](https://user-images.githubusercontent.com/16534043/106468971-f7a2e880-64e1-11eb-9e3e-faf334166094.png)
## TO-BE 조직 (Vertically-Aligned)  
![image](https://user-images.githubusercontent.com/16534043/106469623-de4e6c00-64e2-11eb-9c5d-bd3d43fa6340.png)
## EventStorming 결과
### 완성된 1차 모형  
![image](https://user-images.githubusercontent.com/12531980/106534309-28f9d380-6537-11eb-878b-ae136d43cdcc.png)

### 1차 완성본에 대한 기능적/비기능적 요구사항을 커버하는지 검증  
![image](https://user-images.githubusercontent.com/12531980/106551677-18f2eb80-6559-11eb-907a-7da3b69ce975.png)
```
- 고객이 등록한 레시피를 확인한다. (1, ok)
- 레시피를 등록하면 필요한 재료가 주문이 된다. (2 -> 3, ok)
- 주문 접수가 되면 배송이 되고 주문 상태가 '배송 시작'으로 변경된다. (3 -> 4, ok)
- 고객이 주문 취소를 하게 되면 배달이 취소된다. (5 -> 6, ok)
- 고객은 중간마다 주문상태를 My Page 를 통해 확인 할 수 있다. (7, ok)
```
## 헥사고날 아키텍쳐 다이어그램 도출 (Polyglot)  
![image](https://user-images.githubusercontent.com/12531980/106552529-dd592100-655a-11eb-9d86-dbb94faebe62.png)

# 구현
분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 각 BC별로 대변되는 마이크로 서비스들을 스프링부트로 구현하였다. 구현한 각 서비스를 로컬에서 실행하는 방법은 아래와 같다 (각자의 포트넘버는 8081 ~ 8084, 8088 이다)
```
cd recipe
mvn spring-boot:run  

cd order
mvn spring-boot:run

cd delivery
mvn spring-boot:run 

cd mypage
mvn spring-boot:run  

cd gateway
mvn spring-boot:run  
```

## DDD 의 적용
msaez.io 를 통해 구현한 Aggregate 단위로 Entity 를 선언 후, 구현을 진행하였다.

Entity Pattern 과 Repository Pattern 을 적용하기 위해 Spring Data REST 의 RestRepository 를 적용하였다.

```java
package searchrecipe;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;

@Entity
@Table(name="Order_table")
public class Order {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String materialNm;
    private Integer qty;
    private String status;

    @PostPersist
    public void onPostPersist(){
        Ordered ordered = new Ordered();
        BeanUtils.copyProperties(this, ordered);
        ordered.publishAfterCommit();
    }

    @PrePersist
    public void onPrePersist(){
        try {
            Thread.currentThread().sleep((long) (800 + Math.random() * 220));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @PreRemove
    public void onPreRemove(){
        OrderCanceled orderCanceled = new OrderCanceled();
        BeanUtils.copyProperties(this, orderCanceled);
        orderCanceled.publishAfterCommit();

        //Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.

        searchrecipe.external.Cancellation cancellation = new searchrecipe.external.Cancellation();
        // mappings goes here
        cancellation.setOrderId(this.getId());
        cancellation.setStatus("Delivery Cancelled");
        OrderApplication.applicationContext.getBean(searchrecipe.external.CancellationService.class)
            .cancel(cancellation);

    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public String getMaterialNm() {
        return materialNm;
    }

    public void setMaterialNm(String materialNm) {
        this.materialNm = materialNm;
    }
    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}

```

- 적용 후 REST API의 테스트를 통하여 정상적으로 동작하는 것을 확인할 수 있었다.  
  - 주문 수행 (MaterialOrdered)
  ![image](https://user-images.githubusercontent.com/12531980/106535000-9c501500-6538-11eb-89be-f5c1078ad4c3.png)

  - 주문 목록 조회  
  ![image](https://user-images.githubusercontent.com/12531980/106535116-d6b9b200-6538-11eb-8498-46b2d9398b79.png)

## Gateway 적용
API Gateway를 통하여 마이크로 서비스들의 진입점을 통일하였다.
```yaml
server:
  port: 8088

---

spring:
  profiles: default
  cloud:
    gateway:
      routes:
        - id: recipe
          uri: http://localhost:8081
          predicates:
            - Path=/recipes/** 
        - id: order
          uri: http://localhost:8082
          predicates:
            - Path=/orders/** 
        - id: delivery
          uri: http://localhost:8083
          predicates:
            - Path=/deliveries/**,/cancellations/**
        - id: mypage
          uri: http://localhost:8084
          predicates:
            - Path= /mypages/**
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "*"
            allowedMethods:
              - "*"
            allowedHeaders:
              - "*"
            allowCredentials: true


---

spring:
  profiles: docker
  cloud:
    gateway:
      routes:
        - id: recipe
          uri: http://recipe:8080
          predicates:
            - Path=/recipes/** 
        - id: order
          uri: http://order:8080
          predicates:
            - Path=/orders/** 
        - id: delivery
          uri: http://delivery:8080
          predicates:
            - Path=/deliveries/**,/cancellations/**
        - id: mypage
          uri: http://mypage:8080
          predicates:
            - Path= /mypages/**
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "*"
            allowedMethods:
              - "*"
            allowedHeaders:
              - "*"
            allowCredentials: true

server:
  port: 8080

```


## 폴리그랏 퍼시스턴스
- recipe의 경우, 다른 마이크로 서비스들과 달리 조회 기능도 제공해야 하기에, HSQL을 사용하여 구현하였다. 이를 통해, 마이크로 서비스 간 서로 다른 종류의 데이터베이스를 사용해도 문제 없이 동작하여 폴리그랏 퍼시스턴스를 충족시켰다.

  **recipe 서비스의 pom.xml**  

  ![image](https://user-images.githubusercontent.com/12531980/106535831-70359380-653a-11eb-8e81-1654226aa9e9.png)


## 유비쿼터스 랭귀지
- 조직명, 서비스 명에서 사용되고, 업무현장에서도 쓰이며, 모든 이해관계자들이 직관적으로 의미를 이해할 수 있도록 영어 단어를 사용함 (recipe, order, delivery 등)

## 동기식 호출(Req/Res 방식)과 Fallback 처리
- 분석단계에서의 조건 중 하나로 주문 취소(order)와 배송 취소(delivery) 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 호출 프로토콜은 이미 앞서 Rest Repository 에 의해 노출되어있는 REST 서비스를 FeignClient 를 이용하여 호출하도록 한다.
- 배송 취소 서비스를 호출하기 위하여 FeignClient를 이용하여 Service 대행 인터페이스(Proxy)를 구현
```java
package searchrecipe.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Date;

@FeignClient(name="delivery", url="${api.delivery.url}")
public interface CancellationService {

    @RequestMapping(method= RequestMethod.POST, path="/cancellations")
    public void cancel(@RequestBody Cancellation cancellation);

}
```

- 주문이 취소된 직후(@PreRemove) 배송이 취소되도록 처리
```java
//...
public class Order {
    //...

    @PreRemove
    public void onPreRemove(){
        OrderCanceled orderCanceled = new OrderCanceled();
        BeanUtils.copyProperties(this, orderCanceled);
        orderCanceled.publishAfterCommit();

        //Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.

        searchrecipe.external.Cancellation cancellation = new searchrecipe.external.Cancellation();
        // mappings goes here
        cancellation.setOrderId(this.getId());
        cancellation.setStatus("Delivery Cancelled");
        OrderApplication.applicationContext.getBean(searchrecipe.external.CancellationService.class)
            .cancel(cancellation);
    }
    //...
}
```

- 동기식 호출에서는 호출 시간에 따른 타임 커플링이 발생하여, 주문 취소 시스템에 장애가 나면 배송도 취소되지 않는다는 것을 확인
  - 배송(Delivery) 서비스를 잠시 내려놓음 (ctrl+c)  
  ![image](https://user-images.githubusercontent.com/12531980/106551276-425f4780-6558-11eb-87d0-db00d11f70cb.png)
  - 주문 취소(cancel) 요청 및 에러 난 화면 표시  
  ![image](https://user-images.githubusercontent.com/12531980/106551103-da106600-6557-11eb-8609-4593a0b7d8c2.png)
  - 배송(Delivery) 서비스 재기동 후 다시 주문 취소 요청  
  ![image](https://user-images.githubusercontent.com/12531980/106551365-6d499b80-6558-11eb-84b7-b454b1df15c8.png)

## 비동기식 호출 (Pub/Sub 방식)
- Recipe.java 내에서 아래와 같이 서비스 Pub 구현
```java
//...
public class Recipe {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String recipeNm;
    private String cookingMethod;
    private String materialNm;
    private Integer qty;

    @PostPersist
    public void onPostPersist(){
        MaterialOrdered materialOrdered = new MaterialOrdered();
        BeanUtils.copyProperties(this, materialOrdered);
        materialOrdered.publishAfterCommit();
    }
    //...
}
```

- Order.java 내 Policy Handler 에서 아래와 같이 Sub 구현
```java
//...
@Service
public class PolicyHandler{

    //...
    @Autowired
    OrderRepository orderRepository;

    //...
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverMaterialOrdered_Order(@Payload MaterialOrdered materialOrdered){

        if(materialOrdered.isMe()){
            System.out.println("##### listener  : " + materialOrdered.toJson());
            Order order = new Order();
            order.setMaterialNm(materialOrdered.getMaterialNm());
            order.setQty(materialOrdered.getQty());
            order.setStatus("Received Order");
            orderRepository.save(order);
        }
    }
}
```

- 비동기식 호출은 다른 서비스가 비정상이여도 이상없이 동작가능하여, 주문 서비스에 장애가 나도 레시피 서비스는 정상 동작을 확인
  - Recipe 서비스와 Order 서비스가 둘 다 동시에 돌아가고 있을때 Recipe 서비스 실행시 이상 없음  
  ![image](https://user-images.githubusercontent.com/12531980/106556204-5f007d00-6562-11eb-8087-e0260a54d7bd.png)
  - Order 서비스를 내림  
  ![image](https://user-images.githubusercontent.com/12531980/106555946-e699bc00-6561-11eb-81de-15ea39698d35.png)  
  - Recipe 서비스를 실행하여도 이상 없이 동작    
  ![image](https://user-images.githubusercontent.com/12531980/106556261-7ccde200-6562-11eb-82d1-cd38eb3075fe.png)

## CQRS
viewer를 별도로 구현하여 아래와 같이 view 가 출력된다.
- MaterialOrdered 수행 후의 mypage  
![image](https://user-images.githubusercontent.com/12531980/106606835-ecb18c00-65a5-11eb-85fa-9342cc8bef3d.png)
- OrderCanceled 수행 후의 mypage  
![image](https://user-images.githubusercontent.com/12531980/106606970-17034980-65a6-11eb-91e3-55c4e31a7e36.png)


# 운영
## CI/CD 설정
- git에서 소스 가져오기
```
git clone http://github.com/WonGil/searchrecipe
```

- Build 하기
```
cd /searchrecipe
cd recipe
mvn package

cd ..
cd order
mvn package

cd ..
cd delivery
mvn package

cd ..
cd gateway
mvn package

cd ..
cd mypage
mvn package
```

- Dockerlizing, ACR(Azure Container Registry에 Docker Image Push하기
```
cd /searchrecipe
cd recipe
az acr build --registry skccteam02 --image skccteam02.azurecr.io/recipe:v1 .

cd ..
cd order
az acr build --registry skccteam02 --image skccteam02.azurecr.io/order:v1 .

cd ..
cd delivery
az acr build --registry skccteam02 --image skccteam02.azurecr.io/delivery:v1 .

cd ..
cd gateway
az acr build --registry skccteam02 --image skccteam02.azurecr.io/gateway:v1 .

cd ..
cd mypage
az acr build --registry skccteam02 --image skccteam02.azurecr.io/mypage:v1 .
```

- ACR에서 이미지 가져와서 Kubernetes에서 Deploy하기
```
kubectl create deploy recipe --image=skccteam02.azurecr.io/recipe:v1
kubectl create deploy order --image=skccteam02.azurecr.io/order:v1
kubectl create deploy delivery --image=skccteam02.azurecr.io/delivery:v1
kubectl create deploy gateway --image=skccteam02.azurecr.io/gateway:v1
kubectl create deploy mypage --image=skccteam02.azurecr.io/mypage:v1
kubectl get all
```
- Kubectl Deploy 결과 확인  
![image](https://user-images.githubusercontent.com/16534043/106553685-34f88c00-655d-11eb-87cb-e59a6f920a5b.png)

- Kubernetes에서 서비스 생성하기 (Docker 생성이기에 Port는 8080이며, Gateway는 LoadBalancer로 생성)
```
kubectl expose deploy recipe --type="ClusterIP" --port=8080
kubectl expose deploy order --type="ClusterIP" --port=8080
kubectl expose deploy delivery --type="ClusterIP" --port=8080
kubectl expose deploy gateway --type="LoadBalancer" --port=8080
kubectl expose deploy mypage --type="ClusterIP" --port=8080
kubectl get all
```

- Kubectl Expose 결과 확인  
  ![image](https://user-images.githubusercontent.com/16534043/106554016-e0a1dc00-655d-11eb-8439-f4326cecda5a.png)

- 테스트를 위해서 Kafka zookeeper와 server도 별도로 실행 필요


## 무정지 재배포
- 먼저 무정지 재배포가 100% 되는 것인지 확인하기 위해서 Autoscaler 이나 CB 설정을 제거함

- siege 로 배포작업 직전에 워크로드를 모니터링 함
```
siege -c100 -t60S -r10 -v http get http://delivery:8080/deliveries
```

- Readiness가 설정되지 않은 yml 파일로 배포 진행  
  ![image](https://user-images.githubusercontent.com/16534043/106564492-a261e800-6570-11eb-9b2b-31fca5350825.png)
```
kubectl apply -f deployment_without_readiness.yml
```
- 아래 그림과 같이, Kubernetes가 준비가 되지 않은 delivery pod에 요청을 보내서 siege의 Availability 가 100% 미만으로 떨어짐

- 중간에 socket에 끊겨서 siege 명령어 종료됨 (서비스 정지 발생)  
  ![image](https://user-images.githubusercontent.com/16534043/106564722-fb318080-6570-11eb-92d5-181e50772e8b.png)

- 무정지 재배포 여부 확인 전에, siege 로 배포작업 직전에 워크로드를 모니터링
```
siege -c100 -t60S -r10 -v http get http://delivery:8080/deliveries
```

- Readiness가 설정된 yml 파일로 배포 진행  
  ![image](https://user-images.githubusercontent.com/16534043/106564838-22884d80-6571-11eb-8cf1-dd0e53b547d7.png)
```
kubectl apply -f deployment_with_readiness.yml```
```

- 배포 중 pod가 2개가 뜨고, 새롭게 띄운 pod가 준비될 때까지, 기존 pod가 유지됨을 확인  
  ![image](https://user-images.githubusercontent.com/16534043/106564937-52375580-6571-11eb-994f-b69acceb64b0.png)  
  ![image](https://user-images.githubusercontent.com/16534043/106565031-75620500-6571-11eb-9028-bd05d8125f04.png)

- siege 가 중단되지 않고, Availability가 높아졌음을 확인하여 무정지 재배포가 됨을 확인함  
  ![image](https://user-images.githubusercontent.com/16534043/106565135-a80bfd80-6571-11eb-943e-b3bd77c519db.png)

## 오토스케일 아웃
- 서킷 브레이커는 시스템을 안정되게 운영할 수 있게 해줬지만, 사용자의 요청이 급증하는 경우, 오토스케일 아웃이 필요하다.

  - 단, 부하가 제대로 걸리기 위해서, recipe 서비스의 리소스를 줄여서 재배포한다.
```
kubectl apply -f - <<EOF
  apiVersion: apps/v1
  kind: Deployment
  metadata:
    name: recipe
    namespace: default
    labels:
      app: recipe
  spec:
    replicas: 1
    selector:
      matchLabels:
        app: recipe
    template:
      metadata:
        labels:
          app: recipe
      spec:
        containers:
          - name: recipe
            image: skccteam02.azurecr.io/recipe:v1
            ports:
              - containerPort: 8080
            resources:
              limits:
                cpu: 500m
              requests:
                cpu: 200m
EOF
```

- 다시 expose 해준다.
```
kubectl expose deploy recipe --type="ClusterIP" --port=8080

```

- recipe 시스템에 replica를 자동으로 늘려줄 수 있도록 HPA를 설정한다. 설정은 CPU 사용량이 15%를 넘어서면 replica를 10개까지 늘려준다.
```
kubectl autoscale deploy recipe --min=1 --max=10 --cpu-percent=15
```

- hpa 설정 확인  
  ![image](https://user-images.githubusercontent.com/16534043/106558142-9709bf00-6566-11eb-9340-12959204fee8.png)

- hpa 상세 설정 확인  
  ![image](https://user-images.githubusercontent.com/16534043/106558218-b3a5f700-6566-11eb-9b74-0c93679d2b31.png)
  ![image](https://user-images.githubusercontent.com/16534043/106558245-c0c2e600-6566-11eb-89fe-8a6178e1f976.png)

- siege를 활용해서 워크로드를 2분간 걸어준다. (Cloud 내 siege pod에서 부하줄 것)
```
kubectl exec -it (siege POD 이름) -- /bin/bash
siege -c1000 -t120S -r100 -v --content-type "application/json" 'http://recipe:8080/recipes POST {"recipeNm": "apple_Juice"}'
```

- 오토스케일이 어떻게 되고 있는지 모니터링을 걸어둔다.
```
watch kubectl get all
```
- siege 실행 결과 표시  

![image](https://user-images.githubusercontent.com/16534043/106560612-a12dbc80-656a-11eb-8213-5a07a0a03561.png)

- 오토스케일이 되지 않아, siege 성공률이 낮다.

- 스케일 아웃이 자동으로 되었음을 확인
  ![image](https://user-images.githubusercontent.com/16534043/106560501-75aad200-656a-11eb-99dc-fe585ef7e741.png)

- siege 재실행
```
kubectl exec -it (siege POD 이름) -- /bin/bash
siege -c1000 -t120S -r100 -v --content-type "application/json" 'http://recipe:8080/recipes POST {"recipeNm": "apple_Juice"}'
```

- siege 의 로그를 보아도 전체적인 성공률이 높아진 것을 확인 할 수 있다.  
  ![image](https://user-images.githubusercontent.com/16534043/106560930-3335c500-656b-11eb-8165-bcb066a03f15.png)

## Self-healing (Liveness Probe)
- delivery 시스템 yml 파일의 liveness probe 설정을 바꾸어서, liveness probe가 동작함을 확인

- liveness probe 옵션을 추가하되, 서비스 포트가 아닌 8090으로 설정  
```
        livenessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8090
            initialDelaySeconds: 120
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 5
```

- delivery에 liveness가 적용된 것을 확인  
  ![image](https://user-images.githubusercontent.com/16534043/106566682-f7ebc400-6573-11eb-8452-ed693bdf1f17.png)

- delivery에 liveness가 발동되었고, 8090 포트에 응답이 없기에 Restart가 발생함   
  ![image](https://user-images.githubusercontent.com/16534043/106566789-210c5480-6574-11eb-8e71-ae11755e274f.png)

## 동기식 호출 / 서킷 브레이킹 / 장애격리
- istio를 활용하여 Circuit Breaker 동작을 확인한다.

- istio injection이 enabled 된 namespace를 생성한다.
```
kubectl create namespace istio-test-ns
kubectl label namespace istio-test-ns istio-injection=enabled
```  

- namespace label에 istio-injection이 enabled 된 것을 확인한다.  
  ![image](https://user-images.githubusercontent.com/16534043/106686154-3b464100-660d-11eb-8a64-f9c1c93b35db.png)
  
- 해당 namespace에 기존 서비스들을 재배포한다.
  - 이 명령어로 생성된 pod에 들어가려면 -c 로 컨테이너를 지정해줘야 함
```
# kubectl로 deploy 실행 (실행 위치는 상관없음)
# 이미지 이름과 버전명에 유의
kubectl create deploy recipe --image=skccteam02.azurecr.io/recipe:v1 -n istio-test-ns
kubectl create deploy order --image=skccteam02.azurecr.io/order:v1 -n istio-test-ns
kubectl create deploy delivery --image=skccteam02.azurecr.io/delivery:v1 -n istio-test-ns
kubectl create deploy gateway --image=skccteam02.azurecr.io/gateway:v1 -n istio-test-ns
kubectl create deploy mypage --image=skccteam02.azurecr.io/mypage:v1 -n istio-test-ns
kubectl get all

#expose 하기
# (주의) expose할 때, gateway만 LoadBalancer고, 나머지는 ClusterIP임
kubectl expose deploy recipe --type="ClusterIP" --port=8080 -n istio-test-ns
kubectl expose deploy order --type="ClusterIP" --port=8080 -n istio-test-ns
kubectl expose deploy delivery --type="ClusterIP" --port=8080 -n istio-test-ns
kubectl expose deploy gateway --type="LoadBalancer" --port=8080 -n istio-test-ns
kubectl expose deploy mypage --type="ClusterIP" --port=8080 -n istio-test-ns
```  

- 서비스들이 정상적으로 배포되었고, Container가 2개씩 생성된 것을 확인한다. (1개는 서비스 container, 다른 1개는 Sidecar 형태로 생성된 envoy)  
  ![image](https://user-images.githubusercontent.com/16534043/106686490-b3ad0200-660d-11eb-9473-a779d587f200.png)

- gateway의 External IP를 확인하고, 서비스가 정상 작동함을 확인한다.
```
http http://52.231.71.168:8080/recipes recipeNm=apple_Juice cookingMethod=Using_Mixer materialNm=apple qty=3
```   
  ![image](https://user-images.githubusercontent.com/16534043/106686560-db9c6580-660d-11eb-86f5-c5f5a1b70352.png)

- Circuit Breaker 설정을 위해 아래와 같은 Destination Rule을 생성한다.

- Pending Request가 많을수록 오랫동안 쌓인 요청은 Response Time이 증가하게 되므로, 적절한 대기 쓰레드 풀을 적용하기 위해 connection pool을 설정했다.
```
kubectl apply -f - <<EOF
  apiVersion: networking.istio.io/v1alpha3
  kind: DestinationRule
  metadata:
    name: dr-httpbin
    namespace: istio-test-ns
  spec:
    host: gateway
    trafficPolicy:
      connectionPool:
        http:
          http1MaxPendingRequests: 1
          maxRequestsPerConnection: 1
EOF
```  

- 설정된 Destinationrule을 확인한다.  
  ![image](https://user-images.githubusercontent.com/16534043/106686837-5cf3f800-660e-11eb-9690-3c6ec926bd8e.png)

- siege를 활용하여 User가 1명인 상황에 대해서 요청을 보낸다. (설정값 c1)
  - siege는 같은 namespace에 생성하고, 해당 pod 안에 들어가서 siege 요청을 실행한다.
```
kubectl exec -it siege-5459b87f86-tl584 -c siege -n istio-test-ns -- bin/bash
siege -c1 -t30S -v --content-type "application/json" 'http://52.231.71.168:8080/recipes POST {"recipeNm": "apple_Juice"}'
``` 

- 실행결과를 확인하니, Availability가 높게 나옴을 알 수 있다.  
  ![image](https://user-images.githubusercontent.com/16534043/106687083-d0960500-660e-11eb-9442-f2a4ef3f8da7.png)

- 이번에는 User가 2명인 상황에 대해서 요청을 보내고, 결과를 확인한다.  
```
siege -c2 -t30S -v --content-type "application/json" 'http://52.231.71.168:8080/recipes POST {"recipeNm": "apple_Juice"}'
``` 

- Availability가 User가 1명일 때 보다 낮게 나옴을 알 수있다. Circuit Breaker가 동작하여 대기중인 요청을 끊은 것을 알 수 있다.  
  ![image](https://user-images.githubusercontent.com/16534043/106687175-fcb18600-660e-11eb-8b46-c33a88be8694.png)

## 모니터링, 앨럿팅
- 모니터링: istio가 설치될 때, Add-on으로 설치된 Kiali, Jaeger, Grafana로 데이터, 서비스에 대한 모니터링이 가능하다.

  - Kiali (istio-External-IP:20001)
  어플리케이션의 proxy 통신, 서비스매쉬를 한눈에 쉽게 확인할 수 있는 모니터링 툴  
   ![image](https://user-images.githubusercontent.com/16534043/106687288-31254200-660f-11eb-89d2-61bf7eafa0d9.png)
   ![image](https://user-images.githubusercontent.com/16534043/106687515-97aa6000-660f-11eb-8cad-2247d1d0c747.png)
   
  - Jaeger (istio-External-IP:80)
    트랜잭션을 추적하는 오픈소스로, 이벤트 전체를 파악하는 Tracing 툴  
   ![image](https://user-images.githubusercontent.com/16534043/106687562-b27cd480-660f-11eb-8bb0-0bab4585ece7.png)
   
  - Grafana (istio-External-IP:3000)
  시계열 데이터에 대한 대시보드이며, Prometheus를 통해 수집된 istio 관련 데이터를 보여줌  
  ![image](https://user-images.githubusercontent.com/16534043/106687835-451d7380-6610-11eb-9d54-257c3eb4b866.png)

## ConfigMap 적용
- ConfigMap을 활용하여 변수를 서비스에 이식한다.
- ConfigMap 생성하기
```
kubectl create configmap deliveryword --from-literal=word=Preparing
```  

- Configmap 생성 확인  
  ![image](https://user-images.githubusercontent.com/16534043/106593940-c505f800-6594-11eb-9284-8e896b531f04.png)

- 소스 수정에 따른 Docker 이미지 변경이 필요하기에, 기존 Delivery 서비스 삭제
```
kubectl delete pod,deploy,service delivery
```

- Delivery 서비스의 PolicyHandler.java (delivery\src\main\java\searchrecipe) 수정
```
#30번째 줄을 아래와 같이 수정
#기존에는 Delivery Started라는 고정된 값이 출력되었으나, Configmap에서 가져온 환경변수를 입력받도록 수정
// delivery.setStatus("Delivery Started");
delivery.setStatus(" Delivery Status is " + System.getenv("STATUS"));
```

- Delivery 서비스의 Deployment.yml 파일에 아래 항목 추가하여 deployment_configmap.yml 생성 (아래 코드와 그림은 동일 내용)
```
          env:
            - name: STATUS
              valueFrom:
                configMapKeyRef:
                  name: deliveryword
                  key: word

```  
  ![image](https://user-images.githubusercontent.com/16534043/106592668-275df900-6593-11eb-9007-fb31717f34e8.png)  

- Docker Image 다시 빌드하고, Repository에 배포하기

- Kubernetes에서 POD 생성할 때, 설정한 deployment_configmap.yml 파일로 생성하기
```
kubectl create -f deployment_config.yml
``` 

- Kubernetes에서 POD 생성 후 expose

- 해당 POD에 접속하여 Configmap 항목이 ENV에 있는지 확인  
  ![image](https://user-images.githubusercontent.com/16534043/106595482-faabe080-6596-11eb-9a73-f66fb5d61382.png)

- http로 전송 후, Status에 Configmap의 Key값이 찍히는지 확인  
```
http post http://20.194.26.128:8080/recipes recipeNm=apple_Juice cookingMethod=Using_Mixer materialNm=apple qty=3
```  
  ![image](https://user-images.githubusercontent.com/16534043/106603485-ae19d280-65a1-11eb-9fe5-773e1ad46790.png)
  
- 참고: 기존에 configmap 사용 전에는 아래와 같이 status에 고정값이 출력됨  
  ![image](https://user-images.githubusercontent.com/16534043/106688731-fe307d80-6611-11eb-936f-61739006af67.png)

