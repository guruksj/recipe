# Recommend Recipe

# 서비스 시나리오
## 기능적 요구사항
1. 회원이 레시피를 등록한다.
1. 회원이 등록한 레시피가 추천을 받으면 포인트를 부여한다.
1. 추천 포인트를 회원의 포인트에 업데이트 한다.
1. 회원은 레시피를 삭제할 수 있다.
1. 회원이 레시피를 삭제하면 반드시 추천 레시피의 포인트를 감소시킨다.
1. 회원은 본인의 레시피와 포인트를 중간 중간 조회한다.

## 비기능적 요구사항
1. 트랜잭션
    1. 회원이 레시피를 삭제하면 추천 레시피의 포인트를 즉시 감소시킨다. → Sync 호출
1. 장애격리
    1. 추천 레시피 서비스가 정상 기능이 되지 않더라도 회원은 레시피를 등록 할 수 있다. → Async (event-driven), Eventual Consistency
    1. 레시피 등록이 과중되면 사용자를 잠시동안 받지 않고 잠시후에 등록하도록 유도한다 → Circuit breaker, fallback
1. 성능
    1. 회원은 레시피와 포인트를 확인할 수 있어야 한다. → CQRS 

# 체크포인트
https://workflowy.com/s/assessment/qJn45fBdVZn4atl3

# 분석/설계
## EventStorming 결과
### 완성된 1차 모형  
![01 model](https://user-images.githubusercontent.com/73917331/106879692-d2e78480-671e-11eb-931d-a3eff7013995.PNG)

### 1차 완성본에 대한 기능적/비기능적 요구사항을 커버하는지 검증  
![02 model_review](https://user-images.githubusercontent.com/73917331/106880258-7df83e00-671f-11eb-80b5-812c926dd8b5.png)
```
- 회원이 레시피를 등록한다. (1, ok)
- 회원이 등록한 레시피가 추천을 받으면 포인트를 부여한다. (2, ok)
- 추천 포인트를 회원의 포인트에 업데이트 한다. (3, ok)
- 회원은 레시피를 삭제할 수 있다. ( 4, ok)
- 회원이 레시피를 삭제하면 반드시 추천 레시피의 포인트를 감소시킨다. (5, ok)
- 회원은 본인의 레시피와 포인트를 중간 중간 조회한다. (6, ok)
```
## 헥사고날 아키텍쳐 다이어그램 도출 (Polyglot)  
![03 hexagonal](https://user-images.githubusercontent.com/73917331/106880675-ffe86700-671f-11eb-843f-173fbeca8ae8.png)

# 구현
분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 각 BC별로 대변되는 마이크로 서비스들을 스프링부트로 구현하였다. 구현한 각 서비스를 로컬에서 실행하는 방법은 아래와 같다.
```
cd recipe
mvn spring-boot:run  

cd myrecipe
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
package recipe;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;

@Entity
@Table(name="Myrecipe_table")
public class Myrecipe {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String recipe;
    private Long point;

    @PostPersist
    public void onPostPersist(){
        Registered registered = new Registered();
        BeanUtils.copyProperties(this, registered);
        registered.publishAfterCommit();
    }

    @PreRemove
    public void onPreRemove(){
        Deleted deleted = new Deleted();
        BeanUtils.copyProperties(this, deleted);
        deleted.publishAfterCommit();

        //Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.

        recipe.external.Recipe recipe = new recipe.external.Recipe();
        // mappings goes here
        recipe.setMyrecipeId(this.getId());
        recipe.setPoint(this.point - 1);
        MyrecipeApplication.applicationContext.getBean(recipe.external.RecipeService.class)
            .update(recipe);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public String getRecipe() {
        return recipe;
    }

    public void setRecipe(String recipe) {
        this.recipe = recipe;
    }
    public Long getPoint() {
        return point;
    }

    public void setPoint(Long point) {
        this.point = point;
    }
}

```

- 적용 후 REST API의 테스트를 통하여 정상적으로 동작하는 것을 확인할 수 있었다.  
  - 레시피 등록
  ![1 myrecipes_add](https://user-images.githubusercontent.com/73917331/106910660-c9711300-6744-11eb-9680-d8e95ffeef10.PNG)

  - 레시피 목록 조회  
  ![2 myrecipes_retr](https://user-images.githubusercontent.com/73917331/106910720-da218900-6744-11eb-8440-94bc34bf02fa.PNG)

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
        - id: myrecipe
          uri: http://localhost:8081
          predicates:
            - Path=/myrecipes/** 
        - id: recipe
          uri: http://localhost:8082
          predicates:
            - Path=/recipes/** 
        - id: mypage
          uri: http://localhost:8083
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
        - id: myrecipe
          uri: http://myrecipe:8080
          predicates:
            - Path=/myrecipes/** 
        - id: recipe
          uri: http://recipe:8080
          predicates:
            - Path=/recipes/** 
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
- myrecipe의 경우, 다른 마이크로 서비스들과 달리 조회 기능도 제공해야 하기에, HSQL을 사용하여 구현하였다. 이를 통해, 마이크로 서비스 간 서로 다른 종류의 데이터베이스를 사용해도 문제 없이 동작하여 폴리그랏 퍼시스턴스를 충족시켰다.

  **myrecipe 서비스의 pom.xml**  

  ![3 myrecipe_pom](https://user-images.githubusercontent.com/73917331/106911050-28368c80-6745-11eb-833b-8dba34d0b4f6.PNG)


## 유비쿼터스 랭귀지
- 조직명, 서비스 명에서 사용되고, 업무현장에서도 쓰이며, 모든 이해관계자들이 직관적으로 의미를 이해할 수 있도록 영어 단어를 사용함 (myrecipe, recipe 등)

## 동기식 호출(Req/Res 방식)과 Fallback 처리
- 분석단계에서의 조건 중 하나로 회원 레시피 등록 취소와 추천 포인트 차감 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 호출 프로토콜은 이미 앞서 Rest Repository 에 의해 노출되어있는 REST 서비스를 FeignClient 를 이용하여 호출하도록 한다.
- 레시피 등록 취소 서비스를 호출하기 위하여 FeignClient를 이용하여 Service 대행 인터페이스(Proxy)를 구현
```java
package recipe.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Date;

@FeignClient(name="recipe", url="${api.recipe.url}")
public interface RecipeService {

    @RequestMapping(method= RequestMethod.GET, path="/recipes")
    public void update(@RequestBody Recipe recipe);

}
```

- 레시피 등록이 취소된 직후(@PreRemove) 포인트 차감이 되도록 처리
```java
//...
public class Myrecipe {
    //...

    @PreRemove
    public void onPreRemove(){
        Deleted deleted = new Deleted();
        BeanUtils.copyProperties(this, deleted);
        deleted.publishAfterCommit();

        //Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.

        recipe.external.Recipe recipe = new recipe.external.Recipe();
        // mappings goes here
        recipe.setMyrecipeId(this.getId());
        recipe.setPoint(this.point - 1);
        MyrecipeApplication.applicationContext.getBean(recipe.external.RecipeService.class)
            .update(recipe);
    }
    //...
}
```

- 동기식 호출에서는 호출 시간에 따른 타임 커플링이 발생하여, 레시피 등록 서비스에 장애가 나면 추천 서비스도 동작하지 않는다는 것을 확인
  - 추천 서비스를 잠시 내려놓음 (ctrl+c)  
  ![4 shutdown_recipe](https://user-images.githubusercontent.com/73917331/106912003-feca3080-6745-11eb-9e77-d6716dcd0b49.PNG)
  - 회원 레시피 요청 및 에러 난 화면 표시  
  ![5 recipe_delete](https://user-images.githubusercontent.com/73917331/106912110-0e497980-6746-11eb-8635-3ec8515fd504.PNG)1
  - 추천 서비스 재기동 후 다시 주문 취소 요청  
  ![6 restart_delete](https://user-images.githubusercontent.com/73917331/106912399-52d51500-6746-11eb-91b4-8adac4bfd1a2.PNG)
  ![7 minus_point](https://user-images.githubusercontent.com/73917331/106912835-c37c3180-6746-11eb-87fc-ba4805000e68.PNG)

## 비동기식 호출 (Pub/Sub 방식)
- Myrecipe.java 내에서 아래와 같이 서비스 Pub 구현
```java
//...
public class Myrecipe {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String recipe;
    private Long point;

    @PostPersist
    public void onPostPersist(){
        Registered registered = new Registered();
        BeanUtils.copyProperties(this, registered);
        registered.publishAfterCommit();
    }
    //...
}
```

- Recipe.java 내 Policy Handler 에서 아래와 같이 Sub 구현
```java
//...
@Service
public class PolicyHandler{

    //...
    @Autowired
    RecipeRepository recipeRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverRegistered_(@Payload Registered registered){

        if(registered.isMe()){
            Recipe recipe = new Recipe();

            recipe.setRecipe(registered.getRecipe());
            recipe.setPoint((long)1.0);
            recipe.setMyrecipeId(registered.getId());

            recipeRepository.save(recipe);
            System.out.println("##### listener  : " + registered.toJson());
        }
    }
}
```

- 비동기식 호출은 다른 서비스가 비정상이여도 이상없이 동작가능하여, 레시피 등록 서비스에 장애가 나도 레시피 추천 서비스는 정상 동작을 확인
  - Myrecipe 서비스와 Recipe 서비스가 둘 다 동시에 돌아가고 있을때 Myrecipe 서비스 실행시 이상 없음  
  ![8 normal_add](https://user-images.githubusercontent.com/73917331/106913469-5cab4800-6747-11eb-8576-b7c309540599.PNG)
  - Recipe 서비스를 내림  
  ![9 recipe_down](https://user-images.githubusercontent.com/73917331/106913527-6df45480-6747-11eb-95f3-5202b97ffba4.PNG) 
  - Myrecipe 서비스를 실행하여도 이상 없이 동작    
  ![10 add_myrecipe](https://user-images.githubusercontent.com/73917331/106913600-81072480-6747-11eb-9a95-c0aecaf2cd9f.PNG)

## CQRS
viewer를 별도로 구현하여 아래와 같이 view 가 출력된다.
- Myrecipe 서비스 수행 후의 mypage  
![11 myrecipe_mypage](https://user-images.githubusercontent.com/73917331/106915894-ebb95f80-6749-11eb-85dd-fbcfc26f21ed.png)
- 회원 레시시 삭제 후의 mypage  
![12 delete_mypage](https://user-images.githubusercontent.com/73917331/106913800-b9a6fe00-6747-11eb-8190-7ef4e8e9f1fd.PNG)


# 운영
## CI/CD 설정
- git에서 소스 가져오기
```
git clone http://github.com/guruksj/recipe
```

- Build 하기
```
cd gateway
mvn package

cd ../mypage
mvn package

cd ../myrecipe
mvn package

cd ../recipe
mvn package
```

- Dockerlizing, ACR(Azure Container Registry에 Docker Image Push하기
```
cd ../gateway
az acr build --registry skccuser03 --image skccuser03.azurecr.io/gateway:v1 .

cd ../mypage
az acr build --registry skccuser03 --image skccuser03.azurecr.io/mypage:v1 .

cd ../myrecipe
az acr build --registry skccuser03 --image skccuser03.azurecr.io/myrecipe:v1 .

cd ../recipe
az acr build --registry skccuser03 --image skccuser03.azurecr.io/recipe:v1 .
```

- ACR에서 이미지 가져와서 Kubernetes에서 Deploy하기
```
kubectl create deploy gateway --image=skccuser03.azurecr.io/gateway:v1
kubectl create deploy mypage --image=skccuser03.azurecr.io/mypage:v1
kubectl create deploy myrecipe --image=skccuser03.azurecr.io/myrecipe:v1
kubectl create deploy recipe --image=skccuser03.azurecr.io/recipe:v1
kubectl get all
```
- Kubectl Deploy 결과 확인  
![21 repository](https://user-images.githubusercontent.com/73917331/106883625-95d1c100-6723-11eb-9c8a-444c3fc52c00.png)

- Kubernetes에서 서비스 생성하기 (Docker 생성이기에 Port는 8080이며, Gateway는 LoadBalancer로 생성)
```
kubectl expose deploy gateway --type="LoadBalancer" --port=8080
kubectl expose deploy mypage --type="ClusterIP" --port=8080
kubectl expose deploy myrecipe --type="ClusterIP" --port=8080
kubectl expose deploy recipe --type="ClusterIP" --port=8080
kubectl get all
```

- Kubectl Expose 결과 확인  
 ![22 expose](https://user-images.githubusercontent.com/73917331/106884046-26a89c80-6724-11eb-82a4-004767259a71.png)

- 테스트를 위해서 Kafka zookeeper와 server도 별도로 실행 필요


## 무정지 재배포
- 먼저 무정지 재배포가 100% 되는 것인지 확인하기 위해서 Autoscaler 이나 CB 설정을 제거함

- siege 로 배포작업 직전에 워크로드를 모니터링 함
```
cd recipe/yml
kubectl create -f siege.yaml
kubectl exec -it siege -- /bin/bash
siege -c5 -t120S -v --content-type "application/json" 'http://myrecipe:8080 {"recipe": "recipe44"}'
```

- Readiness가 설정되지 않은 yml 파일로 배포 진행  
  ![23 without readiness](https://user-images.githubusercontent.com/73917331/106887157-1d213380-6728-11eb-993f-3488f57ec517.png)
```
kubectl apply -f deployment_without_readiness.yml
```
- 아래 그림과 같이, Kubernetes가 준비가 되지 않은 delivery pod에 요청을 보내서 siege의 Availability 가 100% 미만으로 떨어짐

- 중간에 socket에 끊겨서 siege 명령어 종료됨 (서비스 정지 발생)  
![24 without_siege](https://user-images.githubusercontent.com/73917331/106893493-f7e4f300-6730-11eb-95bb-60c501182b93.png)

- 무정지 재배포 여부 확인 전에, siege 로 배포작업 직전에 워크로드를 모니터링
```
siege -c5 -t120S -v --content-type "application/json" 'http://myrecipe:8080 {"recipe": "recipe55"}'
```

- Readiness가 설정된 yml 파일로 배포 진행  
  ![25 with_readiness](https://user-images.githubusercontent.com/73917331/106894038-c1f43e80-6731-11eb-9b70-5de2b5c5d4e2.png)
```
kubectl apply -f deployment_with_readiness.yml```
```

- 배포 중 pod가 2개가 뜨고, 새롭게 띄운 pod가 준비될 때까지, 기존 pod가 유지됨을 확인  
  ![26 with_pod1](https://user-images.githubusercontent.com/73917331/106894091-d0daf100-6731-11eb-9f48-7894a7b4094e.png)  
  ![27 with_pod2](https://user-images.githubusercontent.com/73917331/106894125-dc2e1c80-6731-11eb-93c1-ff3c095a0208.png)

- siege 가 중단되지 않고, Availability가 높아졌음을 확인하여 무정지 재배포가 됨을 확인함  
  ![28 with_siege](https://user-images.githubusercontent.com/73917331/106894176-f405a080-6731-11eb-8055-29d9d801da77.png)

## 오토스케일 아웃
- 서킷 브레이커는 시스템을 안정되게 운영할 수 있게 해줬지만, 사용자의 요청이 급증하는 경우, 오토스케일 아웃이 필요하다.

  - 단, 부하가 제대로 걸리기 위해서, recipe 서비스의 리소스를 줄여서 재배포한다.
```
kubectl apply -f - <<EOF
  apiVersion: apps/v1
  kind: Deployment
  metadata:
    name: myrecipe
    namespace: default
    labels:
      app: myrecipe
  spec:
    replicas: 1
    selector:
      matchLabels:
        app: myrecipe
    template:
      metadata:
        labels:
          app: myrecipe
      spec:
        containers:
          - name: myrecipe
            image: skccuser03.azurecr.io/myrecipe:v1
            ports:
              - containerPort: 8080
            resources:
              limits:
                cpu: 100m
              requests:
                cpu: 100m
EOF
```

- 다시 expose 해준다.
```
kubectl delete service myrecipe
kubectl expose deploy myrecipe --type="ClusterIP" --port=8080

```

- myrecipe 시스템에 replica를 자동으로 늘려줄 수 있도록 HPA를 설정한다. 설정은 CPU 사용량이 15%를 넘어서면 replica를 10개까지 늘려준다.
```
kubectl autoscale deploy myrecipe --min=1 --max=10 --cpu-percent=10
```

- hpa 설정 확인  
  ![29 hpa1](https://user-images.githubusercontent.com/73917331/106895348-704cb380-6733-11eb-9197-795dd9300a4e.png))

- hpa 상세 설정 확인  
  ![30 hpa2](https://user-images.githubusercontent.com/73917331/106895375-7b9fdf00-6733-11eb-9cb6-fa939e58e796.png)
  ![31 hpa3](https://user-images.githubusercontent.com/73917331/106895389-7fcbfc80-6733-11eb-9558-42daf4e46001.png)

- siege를 활용해서 워크로드를 2분간 걸어준다. (Cloud 내 siege pod에서 부하줄 것)
```
siege -c1000 -t120S -r100  -v --content-type "application/json" 'http://myrecipe:8080 {"recipe": "recipe66"}'
```

- 오토스케일이 어떻게 되고 있는지 모니터링을 걸어둔다.
```
watch kubectl get all
```
- siege 실행 결과 표시  

![32 hpa_siege](https://user-images.githubusercontent.com/73917331/106898206-3d0c2380-6737-11eb-9378-30c712a207c0.png)g)

- 오토스케일이 되지 않아, siege 성공률이 낮다.

- 스케일 아웃이 자동으로 되었음을 확인
  ![33 hpa_pod](https://user-images.githubusercontent.com/73917331/106898387-76dd2a00-6737-11eb-97d1-88fec2aaa9ab.png)

## Self-healing (Liveness Probe)
- delivery 시스템 yml 파일의 liveness probe 설정을 바꾸어서, liveness probe가 동작함을 확인

- liveness probe 옵션을 추가하되, 서비스 포트가 아닌 8090으로 설정  
```
        livenessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8090
            initialDelaySeconds: 20
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 5
```
kubectl apply -f deployment.yml

- delivery에 liveness가 적용된 것을 확인  
  ![34 liveness1](https://user-images.githubusercontent.com/73917331/106900912-71cdaa00-673a-11eb-82b1-276cefdc637d.png)

- delivery에 liveness가 발동되었고, 8090 포트에 응답이 없기에 Restart가 발생함   
  ![35 liveness2](https://user-images.githubusercontent.com/73917331/106900952-7befa880-673a-11eb-91ca-c07729c0c506.png)

## 동기식 호출 / 서킷 브레이킹 / 장애격리
- istio를 활용하여 Circuit Breaker 동작을 확인한다.

- istio injection이 enabled 된 namespace를 생성한다.
```
kubectl create namespace istio-test-ns
kubectl label namespace istio-test-ns istio-injection=enabled
```  

- namespace label에 istio-injection이 enabled 된 것을 확인한다.  
  ![36 istio1](https://user-images.githubusercontent.com/73917331/106901406-10f2a180-673b-11eb-84f5-b25d0866a715.png)
  
- 해당 namespace에 기존 서비스들을 재배포한다.
  - 이 명령어로 생성된 pod에 들어가려면 -c 로 컨테이너를 지정해줘야 함
```
# kubectl로 deploy 실행 (실행 위치는 상관없음)
# 이미지 이름과 버전명에 유의
kubectl create deploy recipe --image=skccuser03.azurecr.io/recipe:v1 -n istio-test-ns
kubectl create deploy myrecipe --image=skccuser03.azurecr.io/myrecipe:v1 -n istio-test-ns
kubectl create deploy gateway --image=skccuser03.azurecr.io/gateway:v1 -n istio-test-ns
kubectl create deploy mypage --image=skccuser03.azurecr.io/mypage:v1 -n istio-test-ns
kubectl get all

#expose 하기
# (주의) expose할 때, gateway만 LoadBalancer고, 나머지는 ClusterIP임
kubectl expose deploy recipe --type="ClusterIP" --port=8080 -n istio-test-ns
kubectl expose deploy myrecipe --type="ClusterIP" --port=8080 -n istio-test-ns
kubectl expose deploy gateway --type="LoadBalancer" --port=8080 -n istio-test-ns
kubectl expose deploy mypage --type="ClusterIP" --port=8080 -n istio-test-ns
```  

- 서비스들이 정상적으로 배포되었고, Container가 2개씩 생성된 것을 확인한다. (1개는 서비스 container, 다른 1개는 Sidecar 형태로 생성된 envoy)  
  ![36 istio2](https://user-images.githubusercontent.com/73917331/106906070-26b69580-6740-11eb-8aed-9cbee587b74d.png)

- gateway의 External IP를 확인하고, 서비스가 정상 작동함을 확인한다.
```
http http://52.231.76.80:8080/myrecipes recipe=recipe88
```   
  ![38 istio3](https://user-images.githubusercontent.com/73917331/106906289-5c5b7e80-6740-11eb-9057-58cb1e85dab4.png)

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
    host: mypage
    trafficPolicy:
      connectionPool:
        http:
          http1MaxPendingRequests: 1
          maxRequestsPerConnection: 1
EOF
```  

- 설정된 Destinationrule을 확인한다.spec: host:gateway
  ![39 istio_des](https://user-images.githubusercontent.com/73917331/106906902-fe7b6680-6740-11eb-99d6-ac43eaa6425f.png)
  
- siege를 활용하여 User가 1명인 상황에 대해서 요청을 보낸다. (설정값 c1)
  - siege는 같은 namespace에 생성하고, 해당 pod 안에 들어가서 siege 요청을 실행한다.
```
kubectl create -f siege.yaml -n istio-test-ns
kubectl exec -it siege -c siege -n istio-test-ns -- bin/bash
siege -c1 -t30S -v --content-type "application/json" 'http://myrecipe:8080 {"recipe": "recipe800"}'
``` 

- 실행결과를 확인하니, Availability가 높게 나옴을 알 수 있다.  
  ![40 istio_1](https://user-images.githubusercontent.com/73917331/106976601-074d5600-679c-11eb-8dcc-4349e97c7ec1.png)

- 이번에는 User가 3명인 상황에 대해서 요청을 보내고, 결과를 확인한다.  
```
siege -c3 -t30S -v --content-type "application/json" 'http://myrecipe:8080 {"recipe": "recipe800"}'
``` 

- Availability가 User가 1명일 때 보다 낮게 나옴을 알 수있다. Circuit Breaker가 동작하여 대기중인 요청을 끊은 것을 알 수 있다.  
  ![41 istio_3](https://user-images.githubusercontent.com/73917331/106976598-06b4bf80-679c-11eb-9f68-3ee93dbd385b.png)

## 모니터링, 앨럿팅
- 모니터링: istio가 설치될 때, Add-on으로 설치된 Kiali, Jaeger로 데이터, 서비스에 대한 모니터링이 가능하다.

  - Kiali (istio-External-IP:20001)
  어플리케이션의 proxy 통신, 서비스매쉬를 한눈에 쉽게 확인할 수 있는 모니터링 툴  
   ![51 kiali1](https://user-images.githubusercontent.com/73917331/106969884-1aa5f480-678f-11eb-9765-f811176ee555.png)
   ![52 kiali2](https://user-images.githubusercontent.com/73917331/106969891-1bd72180-678f-11eb-81f1-f8a4dfe43692.png)
   
  - Jaeger (istio-External-IP:80)
    트랜잭션을 추적하는 오픈소스로, 이벤트 전체를 파악하는 Tracing 툴  
   ![53 jaeger](https://user-images.githubusercontent.com/73917331/106969893-1d084e80-678f-11eb-8426-146155ac78a9.png)

   
  ## ConfigMap 적용
- ConfigMap을 활용하여 변수를 서비스에 이식한다.
- ConfigMap 생성하기
```
kubectl create configmap deliveryword --from-literal=point=5
```  

- Configmap 생성 확인  
  ![61 config create](https://user-images.githubusercontent.com/73917331/106970362-09111c80-6790-11eb-96ba-a94310545a38.png)

- 소스 수정에 따른 Docker 이미지 변경이 필요하기에, 기존 Delivery 서비스 삭제
```
kubectl delete pod,deploy,service recipe
```

- recipe 서비스의 PolicyHandler.java (recipe\src\main\java\recipe) 수정
```
#28번째 줄을 아래와 같이 수정
#기존에는 1을 부여하였으나, Configmap에서 가져온 환경변수를 입력받도록 수정
// recipe.setPoint((long)1.0);
recipe.setPoint(System.getenv("POINT"));
```

- Recipe 서비스의 Deployment.yml 파일에 아래 항목 추가하여 deployment_configmap.yml 생성 (아래 코드와 그림은 동일 내용)
```
          env:
            - name: STATUS
              valueFrom:
                configMapKeyRef:
                  name: deliveryword
                  key: word

```  
  ![62 cm yml](https://user-images.githubusercontent.com/73917331/106971665-b4bb6c00-6792-11eb-8f17-b45997160a89.png)

- Docker Image 다시 빌드하고, Repository에 배포하기

- Kubernetes에서 POD 생성할 때, 설정한 deployment_configmap.yml 파일로 생성하기
```
kubectl create -f deployment_config.yml
``` 

- Kubernetes에서 POD 생성 후 expose

- 해당 POD에 접속하여 Configmap 항목이 ENV에 있는지 확인  
  ![63 cm env](https://user-images.githubusercontent.com/73917331/106973604-88095380-6796-11eb-913a-db526a982504.png)

- http로 전송 후, Status에 Configmap의 Key값이 찍히는지 확인  
```
http http://52.231.78.198:8080/myrecipes recipe=recipe600
http http://52.231.78.198:8080/recipes
```  
  ![64 cm recipe](https://user-images.githubusercontent.com/73917331/106975130-48903680-6799-11eb-85d6-f946ad5da2d4.png)
  
