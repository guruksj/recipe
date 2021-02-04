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