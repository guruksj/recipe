package recipe;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;

@Entity
@Table(name="Recipe_table")
public class Recipe {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String recipe;
    private Long point;
    private Long myrecipeId;

    @PostPersist
    public void onPostPersist(){
        Recommended recommended = new Recommended();
        BeanUtils.copyProperties(this, recommended);
        recommended.publishAfterCommit();


        Updated updated = new Updated();
        BeanUtils.copyProperties(this, updated);
        updated.publishAfterCommit();


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
    public Long getMyrecipeId() {
        return myrecipeId;
    }

    public void setMyrecipeId(Long myrecipeId) {
        this.myrecipeId = myrecipeId;
    }




}
