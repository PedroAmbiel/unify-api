package br.com.unify.matchable.moderation.services;

import java.util.List;

import br.com.unify.matchable.moderation.dto.ReportCreateRequest;
import br.com.unify.matchable.moderation.dto.ReportReasonOptionResponse;
import br.com.unify.matchable.moderation.dto.ReportResponse;
import br.com.unify.matchable.user.entity.User;

public interface UserReportService {

    ReportResponse createReport(User reporter, ReportCreateRequest request);

    List<ReportReasonOptionResponse> listReasons();
}
