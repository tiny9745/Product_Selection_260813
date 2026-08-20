package com.example.Product_Selection_260813.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.ReviewRecord;

public interface ReviewRecordRepository extends JpaRepository<ReviewRecord, Long> {

    // 單一商品審核歷史（GET /api/products/{id}/reviews）
    List<ReviewRecord> findByProductIdOrderByReviewedAtDesc(Long productId);

    // 決策紀錄列表（GET /api/reviews/decision-records，跨商品彙總查詢）
    Page<ReviewRecord> findAllByOrderByReviewedAtDesc(Pageable pageable);

    // APPROVED商品Final Score凍結快照讀取：核准後不會再重新送審，
    // 故該商品最新一筆審核紀錄即為凍結快照來源
    Optional<ReviewRecord> findFirstByProductIdOrderByReviewedAtDesc(Long productId);
}
