package recipe;

import recipe.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PolicyHandler{
    @StreamListener(KafkaProcessor.INPUT)
    public void onStringEventListener(@Payload String eventString){

    }

    @Autowired
    MyrecipeRepository myrecipeRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverRecommended_UpdatePoint(@Payload Recommended recommended){

        if(recommended.isMe()){
            Optional<Myrecipe> myrecipeOptional = myrecipeRepository.findById(recommended.getMyrecipeId());
            Myrecipe myrecipe = myrecipeOptional.get();
            myrecipe.setPoint(recommended.getPoint());

            myrecipeRepository.save(myrecipe);

            System.out.println("##### listener  : " + recommended.toJson());
        }
    }

}
