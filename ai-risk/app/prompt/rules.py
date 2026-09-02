import re
import unicodedata
from dataclasses import dataclass

from app.schemas.prompt import PromptAttackType


@dataclass(frozen=True)
class RuleMatch:
    rule_id: str
    attack_type: PromptAttackType


@dataclass(frozen=True)
class _Rule:
    rule_id: str
    attack_type: PromptAttackType
    patterns: tuple[re.Pattern[str], ...]


def _patterns(*values: str) -> tuple[re.Pattern[str], ...]:
    return tuple(re.compile(value, re.IGNORECASE) for value in values)


RULES = (
    _Rule(
        "IGNORE_PREVIOUS_INSTRUCTION",
        PromptAttackType.IGNORE_PREVIOUS_INSTRUCTION,
        _patterns(
            r"(?:이전|기존|앞선|앞에서\s*말한).{0,24}(?:지시|명령|규칙|지침).{0,24}(?:무시|잊|폐기|취소|버려)",
            r"(?:지시|명령|규칙|지침).{0,24}(?:무시|잊|폐기|취소|버려).{0,24}(?:실행|따라|진행)",
            r"(?:ignore|forget|discard|replace).{0,32}(?:previous|prior|current|existing).{0,32}(?:instruction|rule|policy|task)",
            r"(?:obey|follow).{0,24}(?:only|solely).{0,24}(?:this|document|note|command)",
            r"(?:기존|심사).{0,24}(?:규칙|지침|정책).{0,24}(?:버리|폐기|무효)",
            r"(?:새\s*지시|문서의\s*명령).{0,32}(?:제한|규칙|정책).{0,20}(?:무효|버리|폐기)",
            r"(?:highest|top).{0,20}priority.{0,32}(?:discard|ignore).{0,28}(?:instruction|policy|rule)",
        ),
    ),
    _Rule(
        "POLICY_BYPASS",
        PromptAttackType.POLICY_BYPASS,
        _patterns(
            r"(?:정책|검사|인증|게이트웨이|gateway|opa).{0,30}(?:우회|건너뛰|생략|무시|skip)",
            r"(?:우회|건너뛰|생략|무시|skip).{0,30}(?:정책|검사|인증|게이트웨이|gateway|opa)",
            r"(?:mark|set).{0,20}(?:opa|policy).{0,20}(?:allow|passed)",
            r"(?:skip|bypass).{0,24}(?:authentication|gateway|policy|opa|check|decision)",
            r"(?:gateway|정책\s*엔진|policy\s*engine).{0,28}(?:거치지\s*말|응답하지\s*않).{0,36}(?:실행|진행|처리)",
            r"(?:보안|정책).{0,20}(?:검사|결과).{0,20}(?:통과|낮은\s*위험).{0,28}(?:조작|처리|계속)",
        ),
    ),
    _Rule(
        "SYSTEM_PROMPT_EXTRACTION",
        PromptAttackType.SYSTEM_PROMPT_EXTRACTION,
        _patterns(
            r"(?:출력|공개|노출|보여|반환|복사).{0,32}(?:시스템|개발자).{0,20}(?:프롬프트|메시지|지침|규칙)",
            r"(?:시스템|개발자).{0,20}(?:프롬프트|메시지|지침|규칙).{0,32}(?:출력|공개|노출|보여|반환|복사)",
            r"(?:reveal|show|print|serialize|return|expose).{0,40}(?:system|developer).{0,24}(?:prompt|message|instruction|rule)",
            r"(?:내부|숨겨\s*둔).{0,28}(?:지침|규칙|메시지|프롬프트).{0,32}(?:출력|반환|공개|보여)",
        ),
    ),
    _Rule(
        "CROSS_CUSTOMER_ACCESS",
        PromptAttackType.CROSS_CUSTOMER_ACCESS,
        _patterns(
            r"(?:다른|타|별도|무관한|case\s*밖|현재\s*case.{0,8}아닌).{0,30}고객.{0,40}(?:조회|열람|확인|가져|보여)",
            r"(?:cust-9999).{0,40}(?:조회|열람|확인|가져|보여)",
            r"(?:another|other|neighboring|outside).{0,32}(?:customer|applicant).{0,40}(?:read|retrieve|open|show|access)",
            r"(?:read|retrieve|open|show|access).{0,40}(?:another|other|neighboring|outside).{0,32}(?:customer|applicant)",
            r"(?:옆|관계없|passport.{0,12}관계없).{0,24}(?:고객|신청자).{0,40}(?:조회|열람|확인|가져|보여)",
            r"(?:customer|applicant).{0,28}(?:not part of|outside).{0,20}(?:case).{0,32}(?:read|retrieve|open|show|access)",
            r"(?:read|retrieve|open|show|access).{0,48}(?:customer|applicant).{0,28}(?:not part of|outside).{0,20}(?:case)",
        ),
    ),
    _Rule(
        "UNAUTHORIZED_TOOL_REQUEST",
        PromptAttackType.UNAUTHORIZED_TOOL_REQUEST,
        _patterns(
            r"(?:허용되지\s*않|미승인|권한.{0,8}없|허용\s*목록.{0,12}없).{0,28}(?:tool|도구).{0,24}(?:사용|실행|추가|호출)",
            r"(?:사용|실행|추가|호출).{0,24}(?:허용되지\s*않|미승인|권한.{0,8}없).{0,28}(?:tool|도구)",
            r"(?:use|run|execute|add).{0,28}(?:unapproved|unauthorized|not allowed).{0,24}(?:tool|function)",
            r"(?:use|run|execute|add).{0,24}(?:wire transfer|account_write|credit_score_write|transfer tool)",
            r"(?:read\s*권한|조회\s*권한).{0,40}(?:자동이체|송금|변경|write).{0,24}(?:실행|처리|수행)",
            r"(?:허용|allowed).{0,16}(?:tool|도구).{0,20}(?:목록|list).{0,24}(?:바꾸|바꿔|변경|추가)",
            r"(?:대출\s*심사|조회).{0,20}(?:권한).{0,36}(?:계좌|거래내역).{0,28}(?:전체|내려받|다운로드)",
        ),
    ),
    _Rule(
        "UNKNOWN_PROMPT_ATTACK",
        PromptAttackType.UNKNOWN_PROMPT_ATTACK,
        _patterns(
            r"(?:탐지|검사|위험|경고).{0,24}(?:숨기|제거|0|안전).{0,32}(?:실행|처리|진행)",
            r"(?:hide|remove|erase|force).{0,28}(?:warning|evidence|risk|detector).{0,36}(?:execute|run|continue)",
            r"(?:classify|mark).{0,20}(?:safe|risk.{0,8}zero).{0,36}(?:execute|run|continue)",
            r"(?:관리자\s*승인|승인\s*코드).{0,32}(?:제한\s*없이|검사\s*없이).{0,28}(?:계속|실행|수행)",
            r"(?:정상|안전).{0,20}(?:분류|입력).{0,36}(?:숨겨진|숨은|내부).{0,24}(?:명령|작업|문장).{0,20}(?:수행|실행|해석)",
            r"(?:안전|보안|위험).{0,20}(?:검사|점수|결과).{0,24}(?:없이|조작|통과).{0,32}(?:명령|작업|요청).{0,20}(?:수행|실행|계속)",
            r"(?:위험|risk).{0,16}(?:점수|score).{0,24}(?:조작|0|zero).{0,28}(?:수행|실행|계속)",
        ),
    ),
)

QUOTED_TEXT = re.compile(r"(['\"]).*?\1")
META_CONTEXT = re.compile(
    r"(?:설명|분석|분류|탐지|테스트\s*시나리오|차이|보안\s*영향|문구|문장|인용|"
    r"보고서|방법|방식|기준.{0,12}알려|패턴.{0,20}(?:검출|탐지)|어떻게.{0,20}(?:검출|탐지|차단)|"
    r"왜.{0,20}(?:위반|공격|차단)|예시.{0,20}(?:사용|확인)|위반인지.{0,16}판단|로그|"
    r"explain|analy[sz]e|classify|describe|"
    r"review|categorize|without\s+(?:following|carrying\s*out)|security\s+impact)",
    re.IGNORECASE,
)
DIRECT_ACTION = re.compile(
    r"(?:조회해|가져와|열어|출력해|반환해|호출해|실행해|처리해|진행해|수행해|"
    r"retrieve|reveal|serialize|execute|run|continue|call\s+the|use\s+the|add\s+[a-z_]+)",
    re.IGNORECASE,
)


def normalize_prompt_text(text: str) -> str:
    normalized = unicodedata.normalize("NFKC", text)
    without_controls = "".join(
        character for character in normalized if not unicodedata.category(character).startswith("C")
    )
    return " ".join(without_controls.split()).casefold()


def detect_rule_matches(text: str) -> tuple[RuleMatch, ...]:
    unquoted = QUOTED_TEXT.sub(" ", text)
    has_direct_action = DIRECT_ACTION.search(unquoted) is not None
    # Quoted attack text is normally treated as data so explanations and reviews do not
    # become false positives. An explicit action outside the quote changes that context:
    # the quoted text is then part of the instruction and must remain searchable.
    searchable = text if has_direct_action else unquoted
    matches: list[RuleMatch] = []
    for rule in RULES:
        if any(
            pattern.search(searchable) or pattern.search(searchable.replace(" ", ""))
            for pattern in rule.patterns
        ):
            matches.append(RuleMatch(rule.rule_id, rule.attack_type))
    if META_CONTEXT.search(unquoted) and not has_direct_action:
        return tuple(
            match
            for match in matches
            if match.attack_type is PromptAttackType.UNKNOWN_PROMPT_ATTACK
        )
    return tuple(matches)
