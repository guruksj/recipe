package recipe;

public class Updated extends AbstractEvent {

    private Long id;
    private Long point;
    private Long myrecipeId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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