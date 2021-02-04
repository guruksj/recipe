package recipe;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MypageRepository extends CrudRepository<Mypage, Long> {

    List<Mypage> findByMyrecipeId(Long myrecipeId);
//    List<> findByMyrecipeId(Long myrecipeId);

}