package com.voltgrid.voltgrid.booking.controller;

import com.voltgrid.voltgrid.booking.dto.BookingRequest;
import com.voltgrid.voltgrid.booking.dto.BookingResponse;
import com.voltgrid.voltgrid.booking.entity.BookingStatus;
import com.voltgrid.voltgrid.booking.service.BookingService;
import com.voltgrid.voltgrid.dto.PageResponse;
import com.voltgrid.voltgrid.security.AuthenticatedUser;
import com.voltgrid.voltgrid.util.Pagination;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
@PreAuthorize("hasRole('DRIVER')")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<BookingResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @Valid @RequestBody BookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.create(user.id(), request));
    }

    @GetMapping
    public PageResponse<BookingResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                              @RequestParam(required = false) BookingStatus status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return bookingService.list(user.id(), status,
                Pagination.of(page, size, Sort.by(Sort.Direction.DESC, "startTime")));
    }

    @GetMapping("/{id}")
    public BookingResponse get(@AuthenticationPrincipal AuthenticatedUser user,
                               @PathVariable Long id) {
        return bookingService.get(user.id(), id);
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@AuthenticationPrincipal AuthenticatedUser user,
                                  @PathVariable Long id) {
        return bookingService.cancel(user.id(), id);
    }
}