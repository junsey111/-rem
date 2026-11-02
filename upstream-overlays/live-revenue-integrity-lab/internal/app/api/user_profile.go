package apiserver

import (
	"database/sql"
	"encoding/json"
	"net/http"
	"strings"

	"github.com/mini_station/live-revenue-integrity-lab/internal/httpx"
)

func (s *Server) handleUserProfile(w http.ResponseWriter, r *http.Request) {
	user := authenticatedUserID(r)
	if r.Method != http.MethodGet && r.Method != http.MethodPut {
		httpx.WriteError(w, http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED", "GET or PUT required")
		return
	}
	if err := s.ensureUser(r.Context(), user); err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	if r.Method == http.MethodPut {
		var req struct {
			Age    int    `json:"age_years"`
			Region string `json:"region_code"`
		}
		if json.NewDecoder(r.Body).Decode(&req) != nil || req.Age < 1 || req.Age > 120 {
			httpx.WriteError(w, http.StatusBadRequest, "INVALID_AGE", "age must be between 1 and 120")
			return
		}
		req.Region = strings.ToUpper(strings.TrimSpace(req.Region))
		const regions = " AD AE AF AG AI AL AM AO AQ AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BL BM BN BO BQ BR BS BT BV BW BY BZ CA CC CD CF CG CH CI CK CL CM CN CO CR CU CV CW CX CY CZ DE DJ DK DM DO DZ EC EE EG EH ER ES ET FI FJ FK FM FO FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GS GT GU GW GY HK HM HN HR HT HU ID IE IL IM IN IO IQ IR IS IT JE JM JO JP KE KG KH KI KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MF MG MH MK ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ NA NC NE NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM PN PR PS PT PW PY QA RE RO RS RU RW SA SB SC SD SE SG SH SI SJ SK SL SM SN SO SR SS ST SV SX SY SZ TC TD TF TG TH TJ TK TL TM TN TO TR TT TV TW TZ UA UG UM US UY UZ VA VC VE VG VI VN VU WF WS YE YT ZA ZM ZW "
		if len(req.Region) != 2 || !strings.Contains(regions, " "+req.Region+" ") {
			httpx.WriteError(w, http.StatusBadRequest, "INVALID_REGION", "two-letter region code required")
			return
		}
		// Self-declared facts do not change moderation or platform permissions.
		if _, err := s.db.ExecContext(r.Context(), `UPDATE users SET age_years=?,region_code=?,profile_source='SELF_REPORTED' WHERE user_id=?`, req.Age, req.Region, user); err != nil {
			httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
	}
	var age sql.NullInt64
	var region sql.NullString
	var source string
	if err := s.db.QueryRowContext(r.Context(), `SELECT CASE WHEN profile_source='SELF_REPORTED' THEN age_years END,CASE WHEN profile_source='SELF_REPORTED' THEN region_code END,profile_source FROM users WHERE user_id=?`, user).Scan(&age, &region, &source); err != nil {
		httpx.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	var ageValue, regionValue any
	if age.Valid {
		ageValue = age.Int64
	}
	if region.Valid {
		regionValue = region.String
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"age_years": ageValue, "region_code": regionValue, "source": source})
}
