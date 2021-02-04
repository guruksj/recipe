package recipe.external;

public class Recipe {

    private Long id;
    private String recipe;
    private Long point;
    private Long myrecipeId;

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
