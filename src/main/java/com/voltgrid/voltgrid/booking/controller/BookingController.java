package com.voltgrid.voltgrid.booking.controller;

import com.voltgrid.voltgrid.booking.dto.BookingRequest;
import com.voltgrid.voltgrid.booking.dto.BookingResponse;
import com.voltgrid.voltgrid.booking.entity.BookingStatus;
import com.voltgrid.voltgrid.booking.service.BookingService;
import com.voltgrid.voltgrid.dto.PageResponse;
import com.voltgrid.voltgrid.payment.service.PaymentService;
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
    private final PaymentService paymentService;

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

    @PostMapping("/{id}/confirm-payment")
    public BookingResponse confirmPayment(@AuthenticationPrincipal AuthenticatedUser user,
                                          @PathVariable Long id) {
        return paymentService.mockConfirmPayment(user.id(), id);
    }

    @PostMapping(value = {"/{id}/start", "/{id}/session/start"})
    public BookingResponse startSession(@AuthenticationPrincipal AuthenticatedUser user,
                                        @PathVariable Long id) {
        return bookingService.startSession(user.id(), id);
    }

    @PostMapping(value = {"/{id}/charge-complete", "/{id}/session/charge-complete"})
    public BookingResponse completeCharging(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PathVariable Long id) {
        return bookingService.completeCharging(user.id(), id);
    }

    @PostMapping(value = {"/{id}/plug-out", "/{id}/session/plug-out"})
    public BookingResponse plugOut(@AuthenticationPrincipal AuthenticatedUser user,
                                   @PathVariable Long id) {
        return bookingService.plugOut(user.id(), id);
    }

    @PostMapping("/{id}/simulate-overstay")
    public BookingResponse simulateOverstay(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @RequestParam(defaultValue = "18") int minutes) {
        return bookingService.simulateOverstay(user.id(), id, minutes);
    }
}