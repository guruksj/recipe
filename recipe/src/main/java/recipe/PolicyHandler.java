package recipe;

import recipe.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class PolicyHandler{
    @StreamListener(KafkaProcessor.INPUT)
    public void onStringEventListener(@Payload String eventString){

    }

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
