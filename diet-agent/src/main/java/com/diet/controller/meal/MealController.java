package com.diet.controller.meal;

import com.diet.model.MealRequest;
import com.diet.model.MealResponse;
import com.diet.security.CurrentUser;
import com.diet.service.meal.MealService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/diet/meals")
public class MealController {
    private final MealService mealService;

    public MealController(MealService mealService) {
        this.mealService = mealService;
    }

    @GetMapping("/personal")
    public List<MealResponse> findPersonal(Authentication authentication) {
        return mealService.findPersonalMeals(CurrentUser.require(authentication).id()).stream().map(MealResponse::from).toList();
    }

    @PostMapping("/personal")
    public MealResponse createPersonal(Authentication authentication, @RequestBody MealRequest request) {
        return MealResponse.from(mealService.createPersonalMeal(CurrentUser.require(authentication).id(), request));
    }

    @PutMapping("/personal/{mealId}")
    public MealResponse updatePersonal(Authentication authentication, @PathVariable Long mealId, @RequestBody MealRequest request) {
        return MealResponse.from(mealService.updatePersonalMeal(CurrentUser.require(authentication).id(), mealId, request));
    }

    @DeleteMapping("/personal/{mealId}")
    public void deletePersonal(Authentication authentication, @PathVariable Long mealId) {
        mealService.deletePersonalMeal(CurrentUser.require(authentication).id(), mealId);
    }

    @GetMapping("/public")
    public List<MealResponse> findPublic() {
        return mealService.findPublicMeals().stream().map(MealResponse::from).toList();
    }
}
